package com.uagrm.repositorio.work.almacenamiento;

import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.exception.NegocioException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Archivos de los trabajos en MinIO (bucket privado: nadie lo lee directamente, todo pasa por work-service).
 * <ul>
 *   <li>El bucket se crea al arrancar si no existe. Si MinIO aun no responde (en docker-compose no tiene
 *       healthcheck) el servicio arranca igual y lo vuelve a intentar antes de la primera subida.</li>
 *   <li>Subida en streaming con partes de 10 MB: en memoria nunca hay mas que una parte.</li>
 *   <li>Cualquier falla de MinIO → 503 (regla 6: uploads 503). Un objeto inexistente al descargar → 404.
 *       Ojo: el cliente 9.x lanza MinioException para respuestas de error, pero las fallas de red (conexion
 *       rechazada, timeout) llegan como IllegalStateException; por eso se capturan ambas.</li>
 * </ul>
 */
@Slf4j
@Component
public class MinioAlmacenamiento implements AlmacenamientoArchivos, ApplicationRunner {

    /** Tamano de cada parte de la subida multipart (minimo de S3: 5 MiB). */
    static final long TAMANO_PARTE = 10L * 1024 * 1024;
    private static final Set<String> NO_EXISTE = Set.of("NoSuchKey", "NoSuchObject");
    private static final Set<String> BUCKET_YA_EXISTE = Set.of("BucketAlreadyOwnedByYou", "BucketAlreadyExists");

    private final MinioClient minio;
    private final String bucket;
    private final AtomicBoolean bucketListo = new AtomicBoolean(false);

    public MinioAlmacenamiento(MinioClient minio, AlmacenamientoProperties props) {
        this.minio = minio;
        this.bucket = props.bucket();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            asegurarBucket();
            log.info("Bucket de MinIO '{}' listo", bucket);
        } catch (NegocioException ex) {
            log.warn("MinIO no responde al arrancar; el bucket '{}' se verificara en la primera subida", bucket);
        }
    }

    @Override
    public void subir(String clave, InputStream contenido, long tamanoBytes, String contentType) {
        asegurarBucket();
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(clave)
                    .stream(contenido, tamanoBytes, TAMANO_PARTE)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | RuntimeException ex) {
            throw noDisponible("subir " + clave, ex);
        }
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
            throw noDisponible("descargar " + clave, ex);
        } catch (MinioException | RuntimeException ex) {
            throw noDisponible("descargar " + clave, ex);
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(clave).build());
        } catch (MinioException | RuntimeException ex) {
            throw noDisponible("borrar " + clave, ex);
        }
    }

    /** Crea el bucket si no existe (una sola vez por proceso; idempotente si dos replicas lo crean a la vez). */
    void asegurarBucket() {
        if (bucketListo.get()) {
            return;
        }
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket de MinIO '{}' creado", bucket);
            }
            bucketListo.set(true);
        } catch (ErrorResponseException ex) {
            if (!BUCKET_YA_EXISTE.contains(ex.errorResponse().code())) {
                throw noDisponible("crear el bucket " + bucket, ex);
            }
            bucketListo.set(true);
        } catch (MinioException | RuntimeException ex) {
            throw noDisponible("verificar el bucket " + bucket, ex);
        }
    }

    private static NegocioException noDisponible(String operacion, Exception ex) {
        log.error("MinIO no disponible al {}: {}", operacion, ex.getMessage());
        return NegocioException.noDisponible(
                "El almacenamiento de archivos no esta disponible. Intente nuevamente en unos minutos.");
    }
}
