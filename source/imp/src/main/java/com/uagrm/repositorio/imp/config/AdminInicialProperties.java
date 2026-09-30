package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.admin-inicial")
public record AdminInicialProperties(
        boolean habilitado,
        @NotBlank String usuario,
        @NotBlank String password,
        @NotBlank @Email String correo,
        @NotNull @Positive Integer ci) {

    @Configuration
    @ConditionalOnProperty(prefix = "app.admin-inicial", name = "habilitado", havingValue = "true")
    @EnableConfigurationProperties(AdminInicialProperties.class)
    static class Registro {
    }
}
