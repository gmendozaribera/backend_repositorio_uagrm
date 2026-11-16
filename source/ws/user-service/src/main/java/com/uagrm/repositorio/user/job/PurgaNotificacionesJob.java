package com.uagrm.repositorio.user.job;

import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Job diario (Quartz, RAMJobStore) que borra las notificaciones LEIDAS mas antiguas que
 * NOTIF_GROUP.RETENCION_DIAS. Horario: app.notificaciones.purga.cron (por defecto 03:00, hora de Bolivia).
 *
 * <p>Reemplaza la tarea original del modelo "limpieza de tokens JWT expirados", que resulto innecesaria: la
 * blacklist y la revocacion de sesiones viven en Redis con TTL, asi que Redis borra solo cada key cuando el token
 * ya no podria usarse; no queda nada que limpiar en la BD.</p>
 *
 * <p>Con varias replicas de user-service cada una ejecuta su propia copia (RAMJobStore no coordina entre
 * instancias). No es un problema: el DELETE es idempotente y la segunda ejecucion simplemente no encuentra filas.</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class PurgaNotificacionesJob extends QuartzJobBean {

    private final NotificacionService notificacionService;

    /** Quartz crea el job con la fabrica de Spring Boot, que inyecta las dependencias por constructor. */
    public PurgaNotificacionesJob(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            notificacionService.purgarLeidasAntiguas();
        } catch (RuntimeException ex) {
            // Un fallo (p. ej. BD caida) no debe matar al scheduler: se reintenta en la proxima ejecucion
            log.warn("Fallo la purga de notificaciones; se reintentara en la proxima ejecucion: {}", ex.getMessage());
        }
    }
}
