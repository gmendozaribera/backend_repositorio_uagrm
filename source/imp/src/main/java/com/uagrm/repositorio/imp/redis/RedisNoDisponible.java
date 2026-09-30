package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.exception.NegocioException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

/**
 * Politica acordada para auth: si Redis no responde se rechaza con 503 (fail-closed),
 * nunca se acepta un token sin poder verificar si fue revocado.
 */
@Slf4j
final class RedisNoDisponible {

    private RedisNoDisponible() {
    }

    static <T> T ejecutar(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException ex) {
            log.error("Redis no disponible: {}", ex.getMessage());
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                    "El servicio de sesiones no esta disponible. Intente nuevamente en unos minutos.");
        }
    }

    static void ejecutar(Runnable operacion) {
        ejecutar(() -> {
            operacion.run();
            return null;
        });
    }
}
