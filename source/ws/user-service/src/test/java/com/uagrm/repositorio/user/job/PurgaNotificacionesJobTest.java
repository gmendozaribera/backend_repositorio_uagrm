package com.uagrm.repositorio.user.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.dao.DataAccessResourceFailureException;

class PurgaNotificacionesJobTest {

    private final NotificacionService notificacionService = mock(NotificacionService.class);

    @Test
    void elJobDelegaLaPurgaAlServicio() {
        new PurgaNotificacionesJob(notificacionService).executeInternal(mock(JobExecutionContext.class));

        verify(notificacionService).purgarLeidasAntiguas();
    }

    @Test
    void unFalloDeLaBdNoRompeElScheduler() {
        when(notificacionService.purgarLeidasAntiguas()).thenThrow(new DataAccessResourceFailureException("BD caida"));

        assertThatCode(() -> new PurgaNotificacionesJob(notificacionService)
                .executeInternal(mock(JobExecutionContext.class))).doesNotThrowAnyException();
    }

    @Test
    void elJobNoSeEjecutaEnParaleloConsigoMismo() {
        assertThat(PurgaNotificacionesJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }

    @Test
    void elTriggerPorDefectoCorreTodosLosDiasALas3HoraDeBolivia() {
        PurgaNotificacionesConfig config = new PurgaNotificacionesConfig();
        JobDetail job = config.purgaNotificacionesJobDetail();
        CronTrigger trigger = (CronTrigger) config.purgaNotificacionesTrigger(job,
                new PurgaNotificacionesConfig.PurgaProperties("0 0 3 * * ?", "America/La_Paz"));

        assertThat(job.isDurable()).isTrue();
        assertThat(trigger.getJobKey()).isEqualTo(job.getKey());
        ZoneId laPaz = ZoneId.of("America/La_Paz");
        // Relativo al inicio del trigger (ahora): una fecha fija deja de funcionar cuando el reloj la pasa
        ZonedDateTime inicio = trigger.getStartTime().toInstant().atZone(laPaz);
        ZonedDateTime esperado = inicio.toLocalDate().atTime(3, 0).atZone(laPaz);
        if (!esperado.isAfter(inicio)) {
            esperado = esperado.plusDays(1);
        }
        Date siguiente = trigger.getFireTimeAfter(Date.from(inicio.toInstant()));
        assertThat(siguiente.toInstant().atZone(laPaz)).isEqualTo(esperado);
    }
}
