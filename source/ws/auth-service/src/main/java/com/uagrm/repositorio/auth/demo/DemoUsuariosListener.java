package com.uagrm.repositorio.auth.demo;

import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Perfil "demo" (nunca en produccion): deja los usuarios de la demo listos para entrar sin pasos extra, en cada
 * arranque. Complementa el seed db/demo/R__demo_ficct.sql, que Flyway aplica ANTES de que exista el admin.
 *
 * <ul>
 *   <li>admin (creado por AdminInicialService, un ApplicationRunner que corre antes de ApplicationReadyEvent) y los
 *       4 usuarios demo: sin cambio de contrasena obligatorio y sin 2FA. Las contrasenas no se tocan.</li>
 *   <li>Borra las caches Redis de catalogos y de la configuracion de FICCT: un listado cacheado antes del seed
 *       (TTL 600 s) ocultaria los datos recien sembrados.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("demo")
public class DemoUsuariosListener {

    static final List<String> USUARIOS_DEMO = List.of("doc.maria", "doc.jose", "219000001", "219000002");

    private final JdbcTemplate jdbc;
    private final CatalogoCacheService catalogoCache;
    private final StringRedisTemplate redis;
    private final String admin;

    public DemoUsuariosListener(JdbcTemplate jdbc, CatalogoCacheService catalogoCache, StringRedisTemplate redis,
                                @Value("${app.admin-inicial.usuario:admin}") String admin) {
        this.jdbc = jdbc;
        this.catalogoCache = catalogoCache;
        this.redis = redis;
        this.admin = admin;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void prepararDemo() {
        Object[] usuarios = Stream.concat(USUARIOS_DEMO.stream(), Stream.of(admin)).toArray();
        String marcadores = String.join(",", Collections.nCopies(usuarios.length, "?"));
        int filas = jdbc.update("UPDATE usuario SET requiere_cambio_password = FALSE, two_factor_enabled = FALSE, "
                + "totp_secret = NULL WHERE usuario IN (" + marcadores + ")", usuarios);
        log.warn("Perfil demo: {} usuarios listos sin cambio de contrasena obligatorio ni 2FA", filas);

        try {
            for (EntidadCatalogo entidad : EntidadCatalogo.values()) {
                catalogoCache.invalidar(entidad);
            }
            jdbc.queryForList("SELECT id_facultad FROM facultad WHERE sigla = 'FICCT'", Integer.class)
                    .forEach(id -> redis.delete(ClavesRedis.configFacultad(id)));
        } catch (DataAccessException ex) {
            log.warn("Perfil demo: no se pudieron limpiar las caches de Redis (expiran por TTL): {}", ex.getMessage());
        }
    }
}
