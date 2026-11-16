package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.config.CatalogoCacheProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache Redis de los listados de catalogos (cache-aside):
 * <ul>
 *   <li>Hit: se devuelve el JSON guardado sin tocar PostgreSQL.</li>
 *   <li>Miss: se consulta PostgreSQL y se guarda con TTL (app.catalogo.cache.ttl).</li>
 *   <li>Escritura (crear/editar/desactivar): se borran todas las keys de esa entidad, usando el SET indice
 *       (nunca KEYS). Si hay transaccion, se borra despues del commit para no recachear datos viejos.</li>
 * </ul>
 * Politica acordada: si Redis falla se DEGRADA a PostgreSQL (log warn), al contrario que auth (fail-closed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoCacheService {

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final CatalogoCacheProperties props;

    /** Listado de activos (filtrado por facultad si idFacultad no es null), desde cache o desde la consulta. */
    public <T> List<T> listar(EntidadCatalogo entidad, Integer idFacultad, Class<T> tipo, Supplier<List<T>> consulta) {
        String clave = ClavesRedis.catalogo(entidad.segmento(), idFacultad);
        List<T> enCache = leer(clave, tipo);
        if (enCache != null) {
            return enCache;
        }
        List<T> datos = consulta.get();
        guardar(entidad, clave, datos);
        return datos;
    }

    /** Borra todos los listados cacheados de la entidad (tras el commit si hay transaccion activa). */
    public void invalidar(EntidadCatalogo entidad) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar(entidad);
                }
            });
        } else {
            borrar(entidad);
        }
    }

    private <T> List<T> leer(String clave, Class<T> tipo) {
        String guardado;
        try {
            guardado = redis.opsForValue().get(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al leer {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return null;
        }
        if (guardado == null) {
            return null;
        }
        try {
            JavaType tipoLista = json.getTypeFactory().constructCollectionType(List.class, tipo);
            return json.readValue(guardado, tipoLista);
        } catch (JacksonException ex) {
            log.warn("Entrada de cache ilegible en {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return null;
        }
    }

    private void guardar(EntidadCatalogo entidad, String clave, List<?> datos) {
        String indice = ClavesRedis.catalogoIndice(entidad.segmento());
        try {
            redis.opsForValue().set(clave, json.writeValueAsString(datos), props.ttl());
            redis.opsForSet().add(indice, clave);
            // El indice vive al menos tanto como la key mas reciente que contiene
            redis.expire(indice, props.ttl());
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al guardar {}; el listado no queda cacheado: {}", clave, ex.getMessage());
        }
    }

    private void borrar(EntidadCatalogo entidad) {
        String indice = ClavesRedis.catalogoIndice(entidad.segmento());
        try {
            Set<String> claves = redis.opsForSet().members(indice);
            List<String> aBorrar = new ArrayList<>(claves == null ? Set.of() : claves);
            aBorrar.add(indice);
            redis.delete(aBorrar);
        } catch (DataAccessException ex) {
            // Sin Redis no hay cache que limpiar ahora; lo que quede expira solo con el TTL
            log.warn("Redis no disponible al invalidar la cache de {}; expirara por TTL: {}",
                    entidad.segmento(), ex.getMessage());
        }
    }
}
