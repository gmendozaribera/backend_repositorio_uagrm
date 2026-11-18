package com.uagrm.repositorio.imp.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** Errores del cliente que Tomcat detecta antes del controller no deben llegar como 500. */
class GlobalExceptionHandlerTest {

    @Test
    void parametroQueNoEsUtf8Devuelve400() {
        ProblemDetail problem = new GlobalExceptionHandler()
                .handleParametroIlegible(new InvalidParameterException("Character decoding failed"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Datos invalidos");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
