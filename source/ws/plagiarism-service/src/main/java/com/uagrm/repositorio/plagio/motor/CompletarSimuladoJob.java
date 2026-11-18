package com.uagrm.repositorio.plagio.motor;

import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Job de una sola ejecucion del motor SIMULADO: completa el analisis {@code idAnalisis}. */
@Slf4j
public class CompletarSimuladoJob extends QuartzJobBean {

    private final MotorSimulado motor;
    private final PlagioService plagioService;

    public CompletarSimuladoJob(MotorSimulado motor, PlagioService plagioService) {
        this.motor = motor;
        this.plagioService = plagioService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        int idAnalisis = context.getMergedJobDataMap().getInt("idAnalisis");
        try {
            motor.completar(idAnalisis);
        } catch (RuntimeException ex) {
            log.error("Fallo la simulacion del analisis {}: {}", idAnalisis, ex.getMessage());
            try {
                plagioService.fallar(idAnalisis, PlagioService.MENSAJE_ENVIO);
            } catch (RuntimeException otra) {
                log.error("Tampoco se pudo marcar el ERROR del analisis {}; lo cerrara el timeout", idAnalisis);
            }
        }
    }
}
