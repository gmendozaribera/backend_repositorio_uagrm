package com.uagrm.repositorio.imp.exception;

import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.security.TokenRevocadoException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte toda excepcion en ProblemDetail (RFC 7807) con status, title, detail y timestamp.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail handleNegocio(NegocioException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getTitle());
        if (ex.getErrores() != null) {
            problem.setProperty("errores", ex.getErrores());
        }
        return conTimestamp(problem);
    }

    /** 429 por intentos fallidos: el cliente sabe cuantos segundos esperar por la cabecera Retry-After. */
    @ExceptionHandler(LimiteIntentosException.class)
    public ResponseEntity<ProblemDetail> handleLimiteIntentos(LimiteIntentosException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSegundos()))
                .body(handleNegocio(ex));
    }

    /**
     * Red de seguridad para duplicados que llegan a la BD (dos requests simultaneos pasan la validacion previa
     * y la restriccion UNIQUE rechaza al segundo). No se expone el mensaje de PostgreSQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El registro entra en conflicto con otro existente o esta en uso.");
        problem.setTitle("Conflicto");
        return conTimestamp(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleNoAutenticado(AuthenticationException ex) {
        String detalle = ex instanceof TokenRevocadoException
                ? "La sesion fue cerrada. Inicie sesion nuevamente."
                : "Token ausente, invalido o expirado.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detalle);
        problem.setTitle("No autenticado");
        return conTimestamp(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccesoDenegado(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean debeCambiarPassword = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> SeguridadConfig.AUTORIDAD_CAMBIO_PASSWORD.equals(a.getAuthority()));
        String detalle = debeCambiarPassword
                ? "Debe cambiar su contrasena antes de continuar (POST /api/v1/auth/password/change)."
                : "No tiene permisos para realizar esta accion.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detalle);
        problem.setTitle("Acceso denegado");
        return conTimestamp(problem);
    }

    /** Request multipart mal formado (cuerpo cortado, boundary invalido). */
    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipart(MultipartException ex) {
        log.warn("Multipart invalido: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No se pudo leer el archivo enviado.");
        problem.setTitle("Archivo invalido");
        return conTimestamp(problem);
    }

    /**
     * Query string con bytes que no son UTF-8 valido (p. ej. "informaci%F3n" codificado en Latin-1): Tomcat no
     * puede decodificar el parametro. Es un error del cliente (400), no del servidor.
     */
    @ExceptionHandler(InvalidParameterException.class)
    public ProblemDetail handleParametroIlegible(InvalidParameterException ex) {
        log.warn("Parametro con codificacion invalida: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Un parametro de la URL no esta codificado en UTF-8.");
        problem.setTitle("Datos invalidos");
        return conTimestamp(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperado(Exception ex) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado. Intente nuevamente.");
        problem.setTitle("Error interno");
        return conTimestamp(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Uno o mas campos no son validos.");
        problem.setTitle("Datos invalidos");
        problem.setProperty("errores", errores);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Archivo mas grande que spring.servlet.multipart.max-file-size: 400 (el contrato no usa 413). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El archivo supera el tamano maximo permitido.");
        problem.setTitle("Archivo invalido");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Agrega timestamp tambien a los ProblemDetail que genera Spring MVC (404, 405, 415, etc.). */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            conTimestamp(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail conTimestamp(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
