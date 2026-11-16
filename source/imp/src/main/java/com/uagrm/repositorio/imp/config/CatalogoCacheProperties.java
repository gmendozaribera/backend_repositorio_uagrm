package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl vida de cada listado cacheado en Redis (acordado: 600 s)
 */
@Validated
@ConfigurationProperties("app.catalogo.cache")
public record CatalogoCacheProperties(@NotNull @DefaultValue("600s") Duration ttl) {

    @Configuration
    @EnableConfigurationProperties(CatalogoCacheProperties.class)
    static class Registro {
    }
}
