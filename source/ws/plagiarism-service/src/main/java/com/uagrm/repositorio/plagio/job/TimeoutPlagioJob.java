package com.uagrm.repositorio.plagio.job;

import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Cada minuto: pasa a ERROR ("El analisis excedio el tiempo maximo.") los analisis EN_PROCESO con mas de
 * PLAGIO_GROUP.TIMEOUT_MINUTOS. Con varias replicas cada una corre su copia: es idempotente (cada analisis se
 * bloquea y solo cambia si sigue EN_PROCESO).
 */
@Slf4j
@DisallowConcurrentExecution
public class TimeoutPlagioJob extends QuartzJobBean {

    private final PlagioService plagioService;

    public TimeoutPlagioJob(PlagioService plagioService) {
        this.plagioService = plagioService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            plagioService.marcarExcedidos();
        } catch (RuntimeException ex) {
            // Un fallo (p. ej. BD caida) no debe matar al scheduler: se reintenta en el siguiente minuto
            log.warn("Fallo el job de timeout de plagio; se reintentara: {}", ex.getMessage());
        }
    }
}
