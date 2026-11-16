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
        Date desde = Date.from(ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, laPaz).toInstant());
        Date siguiente = trigger.getFireTimeAfter(desde);
        assertThat(siguiente.toInstant().atZone(laPaz))
                .isEqualTo(ZonedDateTime.of(2026, 11, 17, 3, 0, 0, 0, laPaz));
    }
}
