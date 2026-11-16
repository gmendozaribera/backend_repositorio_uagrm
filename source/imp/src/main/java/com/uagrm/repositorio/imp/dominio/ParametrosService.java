package com.uagrm.repositorio.imp.dominio;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dao.repository.DominioRepository;
import com.uagrm.repositorio.imp.config.DominioCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Tipo;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lectura de los parametros de la tabla de dominio con cache Redis (cache-aside), comun a todos los servicios:
 * <ul>
 *   <li>Hit: HGETALL de {@code config:dominio:{GRUPO}} (HASH domain_name → domain_value).</li>
 *   <li>Miss: se carga TODO el grupo desde PostgreSQL y se guarda con TTL (app.dominio.cache.ttl, 600 s) en un
 *       solo script Lua (DEL + HSET + EXPIRE): la key nunca queda sin TTL.</li>
 *   <li>Escritura (/api/v1/dominios): se borra la key del grupo despues del commit, asi el cambio se ve al
 *       instante en todos los servicios.</li>
 *   <li>Si Redis falla se DEGRADA a PostgreSQL (log warn), igual que la cache de catalogos.</li>
 *   <li>Si el parametro no existe o su valor es invalido se usa el valor por defecto de
 *       {@link ConstantesDominio} (log warn): un dato mal cargado no tumba el login.</li>
 *   <li>Los secretos (p. ej. SMTP_GROUP.PASSWORD) estan cifrados en la BD y por lo tanto tambien en Redis;
 *       solo {@link #secreto(Parametro)} los descifra, en memoria y para uso interno.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosService {

    /** KEYS[1] = key del grupo; ARGV[1] = TTL en segundos; ARGV[2..] = pares nombre, valor. */
    static final RedisScript<Long> GUARDAR_GRUPO = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], unpack(ARGV, 2))
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return 1""", Long.class);

    private final StringRedisTemplate redis;
    private final DominioRepository repository;
    private final DominioCacheProperties props;
    private final CifradorDominio cifrador;

    /** Valor entero de un parametro del sistema (validado contra su rango; si no, el valor por defecto). */
    public int entero(Parametro parametro) {
        exigirTipo(parametro, Tipo.ENTERO);
        return Integer.parseInt(validado(parametro).trim());
    }

    /** Igual que {@link #entero(Parametro)} buscando el parametro por grupo y nombre (deben estar en ConstantesDominio). */
    public int entero(String grupo, String nombre) {
        return entero(ConstantesDominio.buscar(grupo, nombre).orElseThrow(() -> new IllegalArgumentException(
                "El parametro " + grupo + "." + nombre + " no esta declarado en ConstantesDominio")));
    }

    /** Valor booleano ('true'/'false') de un parametro del sistema; si es invalido, el valor por defecto. */
    public boolean booleano(Parametro parametro) {
        exigirTipo(parametro, Tipo.BOOLEANO);
        return Boolean.parseBoolean(validado(parametro));
    }

    /** Valor de texto (enum, email, host, texto) de un parametro NO secreto, validado; si no, el valor por defecto. */
    public String texto(Parametro parametro) {
        if (parametro.secreto()) {
            throw new IllegalArgumentException("El parametro " + parametro + " es secreto: usar secreto()");
        }
        return validado(parametro);
    }

    /**
     * Valor DESCIFRADO de un parametro secreto ("" si no esta configurado). Solo para uso interno (p. ej. la
     * contrasena del servidor SMTP): nunca devolverlo en una respuesta ni loguearlo. Redis guarda solo el cifrado.
     *
     * @throws IllegalStateException si el servicio no tiene la llave o el valor no se puede descifrar
     */
    public String secreto(Parametro parametro) {
        if (!parametro.secreto()) {
            throw new IllegalArgumentException("El parametro " + parametro + " no es secreto: usar texto()");
        }
        return cifrador.descifrar(valor(parametro.grupo(), parametro.nombre()).orElse(""));
    }

    /** Valor crudo de cualquier parametro del grupo (vacio si no existe). */
    public Optional<String> valor(String grupo, String nombre) {
        return Optional.ofNullable(grupo(grupo).get(nombre));
    }

    /** Borra la cache del grupo (tras el commit si hay transaccion activa). Sin Redis expira sola por TTL. */
    public void invalidar(String grupo) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar(grupo);
                }
            });
        } else {
            borrar(grupo);
        }
    }

    // ---------- privados ----------

    /** Valor crudo si cumple el tipo/rango; si falta o es invalido, el valor por defecto (log warn). */
    private String validado(Parametro parametro) {
        Optional<String> valor = valor(parametro.grupo(), parametro.nombre());
        if (valor.isEmpty()) {
            log.warn("Falta el parametro {}.{} en la tabla dominio; se usa el valor por defecto {}",
                    parametro.grupo(), parametro.nombre(), parametro.porDefecto());
            return parametro.porDefecto();
        }
        Optional<String> error = parametro.validar(valor.get());
        if (error.isPresent()) {
            log.warn("Valor invalido '{}' en {}.{} ({}); se usa el valor por defecto {}", valor.get(),
                    parametro.grupo(), parametro.nombre(), error.get(), parametro.porDefecto());
            return parametro.porDefecto();
        }
        return valor.get();
    }

    private static void exigirTipo(Parametro parametro, Tipo tipo) {
        if (parametro.tipo() != tipo) {
            throw new IllegalArgumentException("El parametro " + parametro + " es de tipo " + parametro.tipo());
        }
    }

    private Map<String, String> grupo(String grupo) {
        String clave = ClavesRedis.dominio(grupo);
        Map<Object, Object> enCache;
        try {
            enCache = redis.opsForHash().entries(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al leer {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return desdeBd(grupo);
        }
        if (enCache != null && !enCache.isEmpty()) {
            Map<String, String> valores = new LinkedHashMap<>();
            enCache.forEach((nombre, valor) -> valores.put(nombre.toString(), valor.toString()));
            return valores;
        }
        Map<String, String> valores = desdeBd(grupo);
        guardar(clave, valores);
        return valores;
    }

    private Map<String, String> desdeBd(String grupo) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (Dominio fila : repository.findByDomainGroupOrderByDomainNameAsc(grupo)) {
            valores.put(fila.getDomainName(), fila.getDomainValue());
        }
        return valores;
    }

    private void guardar(String clave, Map<String, String> valores) {
        if (valores.isEmpty()) {
            return;  // un HASH vacio no existe en Redis: no hay nada que cachear
        }
        List<String> argumentos = new ArrayList<>();
        argumentos.add(Long.toString(props.ttl().toSeconds()));
        valores.forEach((nombre, valor) -> {
            argumentos.add(nombre);
            argumentos.add(valor);
        });
        try {
            redis.execute(GUARDAR_GRUPO, List.of(clave), argumentos.toArray());
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al guardar {}; el grupo no queda cacheado: {}", clave, ex.getMessage());
        }
    }

    private void borrar(String grupo) {
        String clave = ClavesRedis.dominio(grupo);
        try {
            redis.delete(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al invalidar {}; expirara por TTL: {}", clave, ex.getMessage());
        }
    }
}
