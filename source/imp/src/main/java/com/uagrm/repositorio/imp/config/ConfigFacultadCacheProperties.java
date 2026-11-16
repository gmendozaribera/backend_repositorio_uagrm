package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl vida de la configuracion personalizada de una facultad cacheada en Redis, config:facultad:{idFacultad}
 *            (acordado: 600 s). El PUT la borra despues del commit; el TTL solo acota cuanto tarda en verse un
 *            cambio hecho directo en la BD.
 */
@Validated
@ConfigurationProperties("app.config-facultad.cache")
public record ConfigFacultadCacheProperties(@NotNull @DefaultValue("600s") Duration ttl) {

    @Configuration
    @EnableConfigurationProperties(ConfigFacultadCacheProperties.class)
    static class Registro {
    }
}
