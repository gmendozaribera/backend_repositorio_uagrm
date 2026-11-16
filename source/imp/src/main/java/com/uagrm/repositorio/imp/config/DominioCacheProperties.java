package com.uagrm.repositorio.imp.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl vida de cada grupo de parametros cacheado en Redis, config:dominio:{GRUPO} (acordado: 600 s).
 *            Toda escritura por /api/v1/dominios borra la cache del grupo, asi que el TTL solo acota cuanto
 *            puede tardar en verse un cambio hecho directo en la BD.
 */
@Validated
@ConfigurationProperties("app.dominio.cache")
public record DominioCacheProperties(@NotNull @DefaultValue("600s") Duration ttl) {

    @Configuration
    @EnableConfigurationProperties(DominioCacheProperties.class)
    static class Registro {
    }
}
