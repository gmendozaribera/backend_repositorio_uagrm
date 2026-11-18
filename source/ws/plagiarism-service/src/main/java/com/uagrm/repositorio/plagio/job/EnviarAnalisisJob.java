package com.uagrm.repositorio.plagio.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Job de una sola ejecucion: envia el analisis {@code idAnalisis} al motor. */
@Slf4j
public class EnviarAnalisisJob extends QuartzJobBean {

    static final String ID_ANALISIS = "idAnalisis";

    private final ProcesadorPlagio procesador;

    /** Quartz crea el job con la fabrica de Spring Boot, que inyecta las dependencias por constructor. */
    public EnviarAnalisisJob(ProcesadorPlagio procesador) {
        this.procesador = procesador;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        int idAnalisis = context.getMergedJobDataMap().getInt(ID_ANALISIS);
        try {
            procesador.enviar(idAnalisis);
        } catch (RuntimeException ex) {
            // p. ej. BD caida al marcar el ERROR: el job de timeout lo cerrara
            log.error("Fallo el job de envio del analisis {}: {}", idAnalisis, ex.getMessage());
        }
    }
}
