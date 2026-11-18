package com.uagrm.repositorio.plagio.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.plagio.MotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

/** Envio fuera de la transaccion HTTP, despacho por Quartz, timeout periodico y reanudacion al arrancar. */
class PlagioJobsTest {

    private final PlagioService plagioService = mock(PlagioService.class);
    private final MotorPlagio motor = mock(MotorPlagio.class);
    private final Scheduler scheduler = mock(Scheduler.class);

    private static final SolicitudAnalisis SOLICITUD = new SolicitudAnalisis(90, 7, 1, null, false, List.of(
            new SolicitudAnalisis.DocumentoAnalisis(1, "a.pdf", "trabajos/7/a.pdf", "a".repeat(64))));

    @Test
    void enviarDelegaEnElMotorYSiFallaPasaAError() {
        ProcesadorPlagio procesador = new ProcesadorPlagio(plagioService, motor);
        when(plagioService.solicitud(90)).thenReturn(Optional.of(SOLICITUD));
        procesador.enviar(90);
        verify(motor).enviar(SOLICITUD);
        verify(plagioService, never()).fallar(anyInt(), anyString());

        doThrow(NegocioException.noDisponible("MinIO caido")).when(motor).enviar(SOLICITUD);
        procesador.enviar(90);
        verify(plagioService).fallar(90, PlagioService.MENSAJE_ENVIO);
    }

    @Test
    void enviarNoHaceNadaSiElAnalisisYaTermino() {
        when(plagioService.solicitud(90)).thenReturn(Optional.empty());
        new ProcesadorPlagio(plagioService, motor).enviar(90);
        verify(motor, never()).enviar(any());
    }

    @Test
    void despacharAgendaUnJobDeUnSoloDisparoYSiQuartzFallaPasaAError() throws Exception {
        QuartzDespachador despachador = new QuartzDespachador(scheduler, plagioService);
        Instant antes = Instant.now();
        despachador.despachar(90);
        ArgumentCaptor<JobDetail> job = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(job.capture(), trigger.capture());
        assertThat(job.getValue().getJobClass()).isEqualTo(EnviarAnalisisJob.class);
        assertThat(job.getValue().getJobDataMap().getInt("idAnalisis")).isEqualTo(90);
        assertThat(trigger.getValue().getStartTime()).isAfterOrEqualTo(Date.from(antes.minusMillis(1)));

        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenThrow(new SchedulerException("lleno"));
        despachador.despachar(91);
        verify(plagioService).fallar(91, PlagioService.MENSAJE_ENVIO);
    }

    @Test
    void timeoutDelegaYNoRompeElScheduler() {
        TimeoutPlagioJob job = new TimeoutPlagioJob(plagioService);
        job.executeInternal(null);
        verify(plagioService).marcarExcedidos();
        when(plagioService.marcarExcedidos()).thenThrow(new IllegalStateException("BD caida"));
        job.executeInternal(null);  // no lanza
    }

    @Test
    void alArrancarReanudaLosSimuladosOLosCierraSiCambioElMotor() {
        QuartzDespachador despachador = mock(QuartzDespachador.class);
        when(plagioService.enProceso(TipoMotorPlagio.SIMULADO)).thenReturn(List.of(90, 91));

        when(motor.tipo()).thenReturn(TipoMotorPlagio.SIMULADO);
        new ReanudarAnalisis(plagioService, despachador, motor).run(null);
        verify(despachador).despachar(90);
        verify(despachador).despachar(91);

        when(motor.tipo()).thenReturn(TipoMotorPlagio.COPYLEAKS);
        new ReanudarAnalisis(plagioService, despachador, motor).run(null);
        verify(plagioService).fallar(90, PlagioService.MENSAJE_REINICIO);
        verify(plagioService).fallar(91, PlagioService.MENSAJE_REINICIO);
    }
}
