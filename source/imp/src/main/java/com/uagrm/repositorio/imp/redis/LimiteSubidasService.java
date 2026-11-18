package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Limite de subidas de archivos por usuario: {@code WORK_GROUP.UPLOADS_POR_MINUTO} (10 por defecto) en una ventana
 * fija de 60 s, con la key {@code work:rate:upload:{idUsuario}}.
 * <ul>
 *   <li>Cada subida cuenta (exitosa o no) y se cuenta ANTES de leer el archivo: asi un usuario no puede tener al
 *       servidor validando y guardando archivos sin parar, aunque todos terminen rechazados.</li>
 *   <li>Mismo script Lua que el limite de login (INCR + EXPIRE atomicos): la key nunca queda sin TTL.</li>
 *   <li>Al superarlo: 429 con Retry-After = segundos que le quedan a la ventana.</li>
 *   <li>Redis caido → 503 (regla 6: las subidas no se aceptan sin poder aplicar el limite).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimiteSubidasService {

    static final Duration VENTANA = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ParametrosService parametros;

    /** Cuenta una subida del usuario; 429 si con ella supera el maximo de la ventana actual. */
    public void registrar(Integer idUsuario) {
        String clave = ClavesRedis.limiteSubidas(idUsuario);
        int maximo = parametros.entero(Parametro.WORK_UPLOADS_POR_MINUTO);
        Long subidas = ejecutar(() -> redis.execute(LimiteIntentosService.REGISTRAR_FALLO, List.of(clave),
                Long.toString(VENTANA.toSeconds())));
        if (subidas == null || subidas <= maximo) {
            return;
        }
        Long restante = ejecutar(() -> redis.getExpire(clave, TimeUnit.SECONDS));
        long espera = restante == null || restante < 1 ? 1 : restante;
        throw new LimiteIntentosException(espera, "Demasiadas subidas",
                "Supero el limite de " + maximo + " subidas por minuto. Intente de nuevo en " + espera + " segundos.");
    }

    private static <T> T ejecutar(java.util.function.Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException ex) {
            log.error("Redis no disponible para el limite de subidas: {}", ex.getMessage());
            throw NegocioException.noDisponible(
                    "El servicio de subidas no esta disponible. Intente nuevamente en unos minutos.");
        }
    }
}
