package com.uagrm.repositorio.imp.service.catalogo;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadRequest;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.mapper.ConfigFacultadMapper;
import com.uagrm.repositorio.imp.config.ConfigFacultadCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuracion por facultad (tamano de archivo, umbrales de plagio, escala de calificacion, acceso externo,
 * horas de sesion). Si la facultad no guardo la suya se usan los valores por defecto del grupo
 * FACULTAD_DEFAULT_GROUP de la tabla de dominio ({@code personalizada = false}).
 *
 * <p>Cache Redis (cache-aside) SOLO de las configuraciones personalizadas: {@code config:facultad:{idFacultad}}
 * (JSON, TTL app.config-facultad.cache.ttl = 600 s). Los defaults ya estan cacheados en
 * {@code config:dominio:FACULTAD_DEFAULT_GROUP}. El PUT borra la key despues del commit. Si Redis falla se
 * degrada a PostgreSQL (log warn), igual que los catalogos.</p>
 *
 * <p>{@link #configDe(Integer)} es el punto de entrada reutilizable para los demas servicios (auth-service lo usa
 * para la duracion del access token; los modulos de archivos y plagio lo usaran en sus sprints).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigFacultadService {

    private static final String CAMPO_UMBRAL_MEDIO = "umbralPlagioMedio";

    private final ConfigFacultadRepository repository;
    private final FacultadRepository facultadRepository;
    private final ConfigFacultadMapper mapper;
    private final ParametrosService parametros;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final ConfigFacultadCacheProperties props;

    /** GET /api/v1/facultades/{id}/config: 404 si la facultad no existe. */
    @Transactional(readOnly = true)
    public ConfigFacultadResponse obtener(Integer idFacultad) {
        return resolver(idFacultad, true);
    }

    /**
     * Configuracion vigente de la facultad (personalizada o defaults de dominio), sin verificar que exista.
     * Con idFacultad null (p. ej. un admin sin carrera) devuelve los defaults.
     */
    @Transactional(readOnly = true)
    public ConfigFacultadResponse configDe(Integer idFacultad) {
        return idFacultad == null ? porDefecto(null) : resolver(idFacultad, false);
    }

    /** PUT (ADMIN): crea o reemplaza la configuracion propia de la facultad. */
    @Transactional
    public ConfigFacultadResponse actualizar(Integer idFacultad, ConfigFacultadRequest request) {
        Facultad facultad = facultadRepository.findById(idFacultad)
                .orElseThrow(() -> noExiste(idFacultad));
        if (request.umbralPlagioMedio() >= request.umbralPlagioAlto()) {
            throw NegocioException.campoInvalido(CAMPO_UMBRAL_MEDIO, "debe ser menor que umbralPlagioAlto");
        }
        ConfigFacultad config = repository.findByFacultadIdFacultad(idFacultad).orElseGet(() -> {
            ConfigFacultad nueva = new ConfigFacultad();
            nueva.setFacultad(facultad);
            return nueva;
        });
        mapper.actualizar(request, config);
        ConfigFacultad guardada = repository.save(config);
        invalidar(idFacultad);
        return mapper.toResponse(guardada);
    }

    // ---------- privados ----------

    private ConfigFacultadResponse resolver(Integer idFacultad, boolean verificarFacultad) {
        String clave = ClavesRedis.configFacultad(idFacultad);
        ConfigFacultadResponse enCache = leer(clave);
        if (enCache != null) {
            return enCache;
        }
        Optional<ConfigFacultad> fila = repository.findByFacultadIdFacultad(idFacultad);
        if (fila.isPresent()) {
            ConfigFacultadResponse config = mapper.toResponse(fila.get());
            guardar(clave, config);
            return config;
        }
        if (verificarFacultad && !facultadRepository.existsById(idFacultad)) {
            throw noExiste(idFacultad);
        }
        return porDefecto(idFacultad);
    }

    /**
     * Defaults del grupo FACULTAD_DEFAULT_GROUP. Si en dominio quedaron umbrales incoherentes (medio >= alto)
     * se usan los de ConstantesDominio para ambos, igual que con un valor invalido.
     */
    private ConfigFacultadResponse porDefecto(Integer idFacultad) {
        int medio = parametros.entero(Parametro.FACULTAD_UMBRAL_PLAGIO_MEDIO);
        int alto = parametros.entero(Parametro.FACULTAD_UMBRAL_PLAGIO_ALTO);
        if (medio >= alto) {
            log.warn("Umbrales por defecto incoherentes en la tabla dominio (medio {} >= alto {}); se usan {} y {}",
                    medio, alto, Parametro.FACULTAD_UMBRAL_PLAGIO_MEDIO.porDefecto(),
                    Parametro.FACULTAD_UMBRAL_PLAGIO_ALTO.porDefecto());
            medio = Integer.parseInt(Parametro.FACULTAD_UMBRAL_PLAGIO_MEDIO.porDefecto());
            alto = Integer.parseInt(Parametro.FACULTAD_UMBRAL_PLAGIO_ALTO.porDefecto());
        }
        return new ConfigFacultadResponse(idFacultad, false,
                parametros.entero(Parametro.FACULTAD_TAMANO_MAX_ARCHIVO_MB),
                medio,
                alto,
                parametros.booleano(Parametro.FACULTAD_COMPARACION_INTERNET),
                parametros.booleano(Parametro.FACULTAD_ACCESO_EXTERNO),
                parametros.entero(Parametro.FACULTAD_ESCALA_CALIF_MAX),
                parametros.entero(Parametro.FACULTAD_JWT_EXPIRACION_HORAS));
    }

    private ConfigFacultadResponse leer(String clave) {
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
            return json.readValue(guardado, ConfigFacultadResponse.class);
        } catch (JacksonException ex) {
            log.warn("Entrada de cache ilegible en {}; se consulta PostgreSQL: {}", clave, ex.getMessage());
            return null;
        }
    }

    private void guardar(String clave, ConfigFacultadResponse config) {
        try {
            redis.opsForValue().set(clave, json.writeValueAsString(config), props.ttl());
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al guardar {}; la configuracion no queda cacheada: {}", clave, ex.getMessage());
        }
    }

    /** Borra la key despues del commit (asi nadie recachea el valor viejo antes de que se confirme el nuevo). */
    private void invalidar(Integer idFacultad) {
        String clave = ClavesRedis.configFacultad(idFacultad);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar(clave);
                }
            });
        } else {
            borrar(clave);
        }
    }

    private void borrar(String clave) {
        try {
            redis.delete(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al invalidar {}; expirara por TTL: {}", clave, ex.getMessage());
        }
    }

    private static NegocioException noExiste(Integer idFacultad) {
        return NegocioException.noEncontrado("No existe la facultad " + idFacultad + ".");
    }
}
