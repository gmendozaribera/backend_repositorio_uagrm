package com.uagrm.repositorio.mockup.integracion;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integracion de los microservicios (regla 9): PostgreSQL 15 y Redis 7 reales, las mismas
 * imagenes del docker-compose de desarrollo. El esquema lo crean las migraciones reales (Flyway en el test, como
 * RepositorioBusquedaTest y PlagioRepositorioTest); sin Docker los tests se saltan.
 *
 * <p>Uso: {@code class XIntegracionTest extends ContenedoresIntegracion} con {@code @SpringBootTest}. Los
 * servicios sin Flyway en ejecucion (todos menos auth-service) lo declaran como testImplementation.</p>
 *
 * <p>Los contenedores son campos estaticos: se levantan una vez por clase de test y se detienen al terminarla.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class ContenedoresIntegracion {

    @Container
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void contenedores(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
