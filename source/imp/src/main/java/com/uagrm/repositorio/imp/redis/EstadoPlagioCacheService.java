package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.dto.plagio.EstadoPlagioCache;
import com.uagrm.repositorio.imp.config.PlagioCacheProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estado del ultimo analisis de plagio de cada trabajo en Redis: {@code plagio:status:{idTrabajo}} (JSON corto,
 * TTL app.plagio.cache.ttl = 600 s, renovado en cada escritura).
 *
 * <p>Es solo una cache: si Redis falla, leer devuelve vacio (el service consulta la BD) y escribir solo deja un
 * log warn. Nunca responde 503 por esta cache (el 503 por sesion lo sigue dando el filtro de seguridad).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstadoPlagioCacheService {

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final PlagioCacheProperties props;

    /** Estado cacheado; vacio si no existe, es ilegible o Redis no responde. */
    public Optional<EstadoPlagioCache> leer(Integer idTrabajo) {
        String clave = ClavesRedis.estadoPlagio(idTrabajo);
        String guardado;
        try {
            guardado = redis.opsForValue().get(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al leer {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return Optional.empty();
        }
        if (guardado == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(guardado, EstadoPlagioCache.class));
        } catch (JacksonException ex) {
            log.warn("Entrada de cache ilegible en {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Guarda (o reemplaza) el estado con TTL. */
    public void guardar(Integer idTrabajo, EstadoPlagioCache estado) {
        String clave = ClavesRedis.estadoPlagio(idTrabajo);
        try {
            redis.opsForValue().set(clave, json.writeValueAsString(estado), props.ttl());
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al guardar {}; el estado no queda cacheado: {}", clave, ex.getMessage());
            borrarSinRomper(clave);
        }
    }

    /** Si no se pudo escribir, que al menos no quede un estado viejo (en el peor caso vence por TTL). */
    private void borrarSinRomper(String clave) {
        try {
            redis.delete(clave);
        } catch (DataAccessException ex) {
            log.warn("Tampoco se pudo borrar {}; vencera por TTL: {}", clave, ex.getMessage());
        }
    }
}
