package com.uagrm.repositorio.plagio.almacenamiento;

import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.exception.NegocioException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.InputStream;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Archivos de los trabajos en MinIO, SOLO LECTURA: plagiarism-service descarga los documentos para enviarlos al
 * motor externo; nunca sube ni borra (eso es de work-service). Mismo bucket que work-service; no lo crea.
 * Cualquier falla de MinIO → 503; objeto inexistente → 404 (el analisis pasa a ERROR).
 */
@Slf4j
@Component
@EnableConfigurationProperties(AlmacenamientoProperties.class)
public class MinioLectura implements AlmacenamientoArchivos {

    private static final Set<String> NO_EXISTE = Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket");

    private final MinioClient minio;
    private final String bucket;

    public MinioLectura(AlmacenamientoProperties props) {
        // Construir el cliente no abre conexiones: un MinIO caido no impide arrancar el servicio
        this.minio = MinioClient.builder()
                .endpoint(props.url())
                .credentials(props.accessKey(), props.secretKey())
                .build();
        this.bucket = props.bucket();
    }

    @Override
    public void subir(String clave, InputStream contenido, long tamanoBytes, String contentType) {
        throw new UnsupportedOperationException("plagiarism-service solo lee archivos");
    }

    @Override
    public InputStream descargar(String clave) {
        try {
            return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(clave).build());
        } catch (ErrorResponseException ex) {
            if (NO_EXISTE.contains(ex.errorResponse().code())) {
                log.error("El objeto {} esta registrado en la BD pero no existe en MinIO", clave);
                throw NegocioException.noEncontrado("El archivo ya no esta disponible.");
            }
            throw noDisponible(clave, ex);
        } catch (MinioException | RuntimeException ex) {
            // El cliente 9.x lanza IllegalStateException en fallas de red: tambien es "no disponible"
            throw noDisponible(clave, ex);
        }
    }

    @Override
    public void borrar(String clave) {
        throw new UnsupportedOperationException("plagiarism-service solo lee archivos");
    }

    private static NegocioException noDisponible(String clave, Exception ex) {
        log.error("MinIO no disponible al descargar {}: {}", clave, ex.getMessage());
        return NegocioException.noDisponible("El almacenamiento de archivos no esta disponible.");
    }
}
