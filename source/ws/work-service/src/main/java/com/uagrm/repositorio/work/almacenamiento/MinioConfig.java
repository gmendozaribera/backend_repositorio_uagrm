package com.uagrm.repositorio.work.almacenamiento;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cliente de MinIO. Construirlo no abre conexiones: un MinIO caido no impide arrancar el servicio. */
@Configuration
@EnableConfigurationProperties(AlmacenamientoProperties.class)
public class MinioConfig {

    @Bean
    MinioClient minioClient(AlmacenamientoProperties props) {
        return MinioClient.builder()
                .endpoint(props.url())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}
