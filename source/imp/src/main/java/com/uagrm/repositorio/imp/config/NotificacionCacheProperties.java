package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl vida del contador de no leidas notif:pendientes:{idUsuario} (acordado: 1 dia). Se renueva en cada
 *            escritura; si expira solo cuesta un COUNT en PostgreSQL en la siguiente consulta de la campana.
 */
@Validated
@ConfigurationProperties("app.notificaciones.cache")
public record NotificacionCacheProperties(@NotNull @DefaultValue("1d") Duration ttl) {

    @Configuration
    @EnableConfigurationProperties(NotificacionCacheProperties.class)
    static class Registro {
    }
}
