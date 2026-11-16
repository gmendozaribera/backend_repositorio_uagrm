package com.uagrm.repositorio.imp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 429: la IP supero los intentos fallidos permitidos. El GlobalExceptionHandler agrega la cabecera
 * Retry-After con los segundos que faltan para que se desbloquee.
 */
@Getter
public class LimiteIntentosException extends NegocioException {

    private final long retryAfterSegundos;

    public LimiteIntentosException(long retryAfterSegundos) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
                "Demasiados intentos fallidos. Intente de nuevo en " + minutos(retryAfterSegundos) + " minutos.");
        this.retryAfterSegundos = retryAfterSegundos;
    }

    /** Redondeo hacia arriba: con 61 s restantes se informa 2 minutos, nunca 0. */
    private static long minutos(long segundos) {
        return Math.max(1, (segundos + 59) / 60);
    }
}
