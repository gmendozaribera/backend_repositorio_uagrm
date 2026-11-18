package com.uagrm.repositorio.work.almacenamiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Conexion a MinIO (infraestructura: variables de entorno, nunca la tabla dominio).
 *
 * @param url       MINIO_URL, p. ej. http://minio:9000
 * @param accessKey MINIO_ACCESS_KEY (Secret)
 * @param secretKey MINIO_SECRET_KEY (Secret)
 * @param bucket    MINIO_BUCKET (por defecto "trabajos"); se crea al arrancar si no existe
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
