package com.uagrm.repositorio.plagio.almacenamiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Conexion a MinIO (mismas variables que work-service: MINIO_URL, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET).
 * En produccion conviene un usuario de MinIO propio con permiso SOLO de lectura sobre el bucket.
 */
@Validated
@ConfigurationProperties(prefix = "app.almacenamiento")
public record AlmacenamientoProperties(
        @NotBlank String url,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$") String bucket) {

    @Override
    public String toString() {
        return "AlmacenamientoProperties[url=" + url + ", bucket=" + bucket + "]";  // sin credenciales en logs
    }
}
