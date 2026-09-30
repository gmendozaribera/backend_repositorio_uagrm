package com.uagrm.repositorio.imp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.seguridad")
public record SeguridadProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull DosFactores dosFactores,
        @NotEmpty List<String> corsOrigenes) {

    /**
     * @param secreto              clave HMAC en Base64, minimo 256 bits (32 bytes)
     * @param accessTtlPorDefecto  se usa cuando el usuario no pertenece a una facultad con ConfigFacultad
     */
    public record Jwt(
            @NotBlank String secreto,
            @NotBlank String emisor,
            @NotNull Duration accessTtlPorDefecto,
            @NotNull Duration refreshTtl) {
    }

    /**
     * @param emisor        nombre que muestra Google Authenticator junto a la cuenta
     * @param ttlPendiente  tiempo para completar el paso 2 del login
     * @param maxIntentos   codigos erroneos permitidos antes de anular la sesion pendiente
     */
    public record DosFactores(
            @NotBlank String emisor,
            @NotNull Duration ttlPendiente,
            @Min(1) int maxIntentos) {
    }
}
