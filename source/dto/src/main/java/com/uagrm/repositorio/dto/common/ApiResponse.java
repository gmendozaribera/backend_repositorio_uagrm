package com.uagrm.repositorio.dto.common;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Formato estandar de toda respuesta exitosa de la API: { data, message, status, timestamp }.
 * Los errores no usan esta clase: se devuelven como ProblemDetail (RFC 7807).
 */
public record ApiResponse<T>(T data, String message, int status, Instant timestamp) {

    public static <T> ApiResponse<T> of(HttpStatus status, String message, T data) {
        return new ApiResponse<>(data, message, status.value(), Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return of(HttpStatus.CREATED, message, data);
    }
}
