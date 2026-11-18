package com.uagrm.repositorio.work.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uagrm.repositorio.imp.exception.NegocioException;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integracion real con MinIO (Testcontainers, regla 9). Se salta si no hay Docker disponible.
 * Usa la misma imagen que docker-compose dev (Chainguard: Docker Hub dejo de publicar minio/minio).
 */
@Testcontainers(disabledWithoutDocker = true)
class MinioAlmacenamientoTest {

    @Container
    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"));

    private static MinioAlmacenamiento almacenamiento;

    @BeforeAll
    static void crear() {
        AlmacenamientoProperties props = new AlmacenamientoProperties(MINIO.getS3URL(), MINIO.getUserName(),
                MINIO.getPassword(), "trabajos-test");
        MinioClient cliente = new MinioConfig().minioClient(props);
        almacenamiento = new MinioAlmacenamiento(cliente, props);
        almacenamiento.run(null);  // crea el bucket como al arrancar
    }

    @Test
    void subirDescargarYBorrarUnArchivoMultiparte() throws Exception {
        // 12 MB: mas grande que una parte (10 MB), asi se ejercita la subida multipart en streaming
        byte[] contenido = new byte[12 * 1024 * 1024];
        new Random(7).nextBytes(contenido);
        String clave = "trabajos/7/11111111-2222-3333-4444-555555555555.pdf";

        almacenamiento.subir(clave, new ByteArrayInputStream(contenido), contenido.length, "application/pdf");
        try (InputStream descargado = almacenamiento.descargar(clave)) {
            assertThat(descargado.readAllBytes()).isEqualTo(contenido);
        }

        almacenamiento.borrar(clave);
        assertThatThrownBy(() -> almacenamiento.descargar(clave))
                .isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        assertThatNoException().isThrownBy(() -> almacenamiento.borrar(clave));  // borrar dos veces no falla
    }

    @Test
    void bucketYaExistenteNoEsError() {
        AlmacenamientoProperties props = new AlmacenamientoProperties(MINIO.getS3URL(), MINIO.getUserName(),
                MINIO.getPassword(), "trabajos-test");
        MinioAlmacenamiento otraReplica = new MinioAlmacenamiento(new MinioConfig().minioClient(props), props);
        assertThatNoException().isThrownBy(otraReplica::asegurarBucket);
    }

    @Test
    void minioInalcanzableDevuelve503() {
        AlmacenamientoProperties props = new AlmacenamientoProperties("http://127.0.0.1:1", "x", "y", "trabajos-test");
        MinioAlmacenamiento caido = new MinioAlmacenamiento(new MinioConfig().minioClient(props), props);
        assertThatThrownBy(() -> caido.subir("trabajos/1/a.pdf", new ByteArrayInputStream(new byte[1]), 1,
                "application/pdf"))
                .isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
