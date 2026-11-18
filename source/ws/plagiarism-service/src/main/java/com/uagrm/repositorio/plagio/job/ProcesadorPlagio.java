package com.uagrm.repositorio.plagio.job;

import com.uagrm.repositorio.imp.service.plagio.MotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Envia un analisis al motor configurado (lo llama el job de Quartz, fuera de la transaccion HTTP).
 * Si el envio falla, el analisis pasa a ERROR con un mensaje legible (el detalle tecnico queda solo en el log).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesadorPlagio {

    private final PlagioService plagioService;
    private final MotorPlagio motor;

    public void enviar(Integer idAnalisis) {
        Optional<SolicitudAnalisis> solicitud = plagioService.solicitud(idAnalisis);
        if (solicitud.isEmpty()) {
            log.info("Analisis {} ya no esta en curso: no se envia", idAnalisis);
            return;
        }
        if (solicitud.get().documentos().isEmpty()) {
            log.warn("Analisis {} en curso sin documentos pendientes: se espera el timeout", idAnalisis);
            return;
        }
        try {
            motor.enviar(solicitud.get());
        } catch (RuntimeException ex) {
            log.error("Fallo el envio del analisis {} al motor {}: {} {}", idAnalisis, motor.tipo(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            plagioService.fallar(idAnalisis, PlagioService.MENSAJE_ENVIO);
        }
    }
}
