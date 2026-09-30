package com.uagrm.repositorio.imp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error de regla de negocio. El GlobalExceptionHandler la convierte en ProblemDetail
 * con el status, title y detail indicados.
 */
@Getter
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public NegocioException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public static NegocioException noEncontrado(String detail) {
        return new NegocioException(HttpStatus.NOT_FOUND, "Recurso no encontrado", detail);
    }

    public static NegocioException conflicto(String detail) {
        return new NegocioException(HttpStatus.CONFLICT, "Conflicto", detail);
    }

    public static NegocioException noAutorizado(String detail) {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "No autorizado", detail);
    }
}
