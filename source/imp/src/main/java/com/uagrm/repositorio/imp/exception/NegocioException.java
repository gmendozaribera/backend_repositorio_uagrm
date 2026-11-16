package com.uagrm.repositorio.imp.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error de regla de negocio. El GlobalExceptionHandler la convierte en ProblemDetail
 * con el status, title y detail indicados (y errores campo -> mensaje si los tiene).
 */
@Getter
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    /** Solo en errores de validacion de un campo concreto; null en el resto. */
    private final Map<String, String> errores;

    public NegocioException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null);
    }

    private NegocioException(HttpStatus status, String title, String detail, Map<String, String> errores) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errores = errores;
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

    /** 400 con el mismo formato que Bean Validation: errores = { campo: mensaje }. */
    public static NegocioException campoInvalido(String campo, String mensaje) {
        return new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos", "Uno o mas campos no son validos.",
                Map.of(campo, mensaje));
    }
}
