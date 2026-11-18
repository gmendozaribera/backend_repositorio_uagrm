package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl vida de plagio:status:{idTrabajo} (acordado: 600 s). Se renueva en cada cambio de estado del
 *            analisis; si expira, la siguiente consulta de /estado lee la BD y vuelve a guardarlo.
 */
@Validated
@ConfigurationProperties("app.plagio.cache")
public record PlagioCacheProperties(@NotNull @DefaultValue("600s") Duration ttl) {

    @Configuration
    @EnableConfigurationProperties(PlagioCacheProperties.class)
    static class Registro {
    }
}
