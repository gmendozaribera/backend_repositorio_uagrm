package com.uagrm.repositorio.plagio.job;

import com.uagrm.repositorio.imp.service.plagio.DespachadorPlagio;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

/**
 * Agenda trabajos de un solo disparo en el scheduler de Quartz (RAMJobStore). Si Quartz rechaza el trabajo el
 * analisis pasa a ERROR al momento (no hay que esperar al timeout).
 */
@Slf4j
@Component
public class QuartzDespachador implements DespachadorPlagio {

    private final Scheduler scheduler;
    private final PlagioService plagioService;

    /** Sin ciclo: PlagioService pide el despachador con ObjectProvider solo al usarlo. */
    public QuartzDespachador(Scheduler scheduler, PlagioService plagioService) {
        this.scheduler = scheduler;
        this.plagioService = plagioService;
    }

    @Override
    public void despachar(Integer idAnalisis) {
        agendar(EnviarAnalisisJob.class, "enviar", idAnalisis, Instant.now());
    }

    /** Ejecuta {@code tipo} con el idAnalisis en el JobDataMap en el instante dado (una sola vez). */
    public void agendar(Class<? extends Job> tipo, String prefijo, Integer idAnalisis, Instant cuando) {
        String nombre = prefijo + "-" + idAnalisis + "-" + UUID.randomUUID();
        JobDetail job = JobBuilder.newJob(tipo)
                .withIdentity(nombre, "plagio")
                .usingJobData(EnviarAnalisisJob.ID_ANALISIS, idAnalisis)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(nombre, "plagio")
                .startAt(Date.from(cuando))
                // Si el scheduler estaba ocupado y se paso la hora, se ejecuta igual apenas pueda
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException ex) {
            log.error("Quartz rechazo el trabajo {} del analisis {}: {}", prefijo, idAnalisis, ex.getMessage());
            plagioService.fallar(idAnalisis, PlagioService.MENSAJE_ENVIO);
        }
    }
}
