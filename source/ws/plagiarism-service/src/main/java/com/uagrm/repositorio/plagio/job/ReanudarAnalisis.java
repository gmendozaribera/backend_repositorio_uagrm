package com.uagrm.repositorio.plagio.job;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.MotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar: el scheduler esta en memoria (RAMJobStore), asi que un reinicio pierde los trabajos agendados.
 * <ul>
 *   <li>Analisis SIMULADO en curso y el motor actual es SIMULADO: se vuelven a despachar. El simulador es
 *       deterministico y solo se reenvian los documentos sin resultado, asi que el resultado es el mismo.</li>
 *   <li>Analisis SIMULADO en curso pero el motor actual es COPYLEAKS (se cambio PLAGIO_MOTOR): ERROR
 *       ("se interrumpio por un reinicio"); el docente puede iniciarlo de nuevo.</li>
 *   <li>Analisis COPYLEAKS en curso: no se hace nada. Copyleaks guarda el scan y avisara por webhook aunque el
 *       servicio se haya reiniciado; si nunca avisa (p. ej. el reinicio fue antes de enviarlo), el job de timeout
 *       lo pasa a ERROR a los TIMEOUT_MINUTOS.</li>
 * </ul>
 * Con varias replicas cada una reanuda los simulados: no hay problema porque registrar un resultado es idempotente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReanudarAnalisis implements ApplicationRunner {

    private final PlagioService plagioService;
    private final QuartzDespachador despachador;
    private final MotorPlagio motor;

    @Override
    public void run(ApplicationArguments args) {
        List<Integer> simulados;
        try {
            simulados = plagioService.enProceso(TipoMotorPlagio.SIMULADO);
        } catch (RuntimeException ex) {
            log.warn("No se pudieron revisar los analisis en curso al arrancar (el timeout los cerrara): {}",
                    ex.getMessage());
            return;
        }
        for (Integer id : simulados) {
            if (motor.tipo() == TipoMotorPlagio.SIMULADO) {
                despachador.despachar(id);
            } else {
                plagioService.fallar(id, PlagioService.MENSAJE_REINICIO);
            }
        }
        if (!simulados.isEmpty()) {
            log.info("{} analisis SIMULADO en curso al arrancar: {}", simulados.size(),
                    motor.tipo() == TipoMotorPlagio.SIMULADO ? "reanudados" : "pasados a ERROR");
        }
    }
}
