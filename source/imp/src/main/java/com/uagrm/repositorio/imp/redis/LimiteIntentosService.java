package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.security.IpCliente;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Limite de intentos FALLIDOS por IP en el login y el auto-registro (proteccion contra fuerza bruta):
 * como un cajero que retiene la tarjeta tras varios PIN incorrectos, pero por IP y por un tiempo.
 * <ul>
 *   <li>Antes de procesar: si la IP ya acumulo {@code AUTH_GROUP.LOGIN_INTENTOS} fallos → 429 con Retry-After.</li>
 *   <li>Cada fallo suma 1. El primero arranca el reloj: la key vive {@code AUTH_GROUP.LOGIN_WAITING} minutos y
 *       luego se borra sola (ventana fija). Los intentos exitosos NO cuentan ni reinician el contador:
 *       muchos estudiantes comparten la IP de un laboratorio y un atacante con una cuenta valida no debe
 *       poder "limpiar" sus fallos iniciando sesion.</li>
 *   <li>Los limites se leen de la tabla de dominio (cache Redis): un cambio por /api/v1/dominios aplica al instante.</li>
 *   <li>Redis caido → 503 (auth es fail-closed: sin contador no se puede frenar un ataque).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LimiteIntentosService {

    /**
     * INCR + EXPIRE atomico: si la key no tiene TTL (recien creada, o por cualquier motivo quedo sin el) se le pone.
     * KEYS[1] = contador; ARGV[1] = segundos de bloqueo. Devuelve el contador.
     */
    static final RedisScript<Long> REGISTRAR_FALLO = RedisScript.of("""
            local intentos = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return intentos""", Long.class);

    /** Operaciones con limite propio (contadores separados). */
    public enum Operacion {
        LOGIN(ClavesRedis::limiteLogin),
        REGISTRO(ClavesRedis::limiteRegistro);

        private final Function<String, String> clave;

        Operacion(Function<String, String> clave) {
            this.clave = clave;
        }
    }

    private final StringRedisTemplate redis;
    private final ParametrosService parametros;

    /** 429 si la IP ya agoto sus intentos; si no, no hace nada. */
    public void verificar(Operacion operacion, String ip) {
        String clave = operacion.clave.apply(IpCliente.normalizar(ip));
        String guardado = RedisNoDisponible.ejecutar(() -> redis.opsForValue().get(clave));
        if (guardado == null || intentos(guardado) < parametros.entero(Parametro.AUTH_LOGIN_INTENTOS)) {
            return;
        }
        Long restante = RedisNoDisponible.ejecutar(() -> redis.getExpire(clave, TimeUnit.SECONDS));
        if (restante == null || restante == -2) {
            return;  // expiro entre las dos lecturas: ya esta desbloqueada
        }
        if (restante < 0) {
            // Key sin TTL (no deberia pasar con el script): se le pone para que el bloqueo no sea eterno
            long espera = bloqueo().toSeconds();
            RedisNoDisponible.ejecutar(() -> redis.expire(clave, Duration.ofSeconds(espera)));
            restante = espera;
        }
        throw new LimiteIntentosException(Math.max(1, restante));
    }

    /** Suma un intento fallido de la IP y devuelve cuantos lleva en la ventana actual. */
    public long registrarFallo(Operacion operacion, String ip) {
        String clave = operacion.clave.apply(IpCliente.normalizar(ip));
        String segundos = Long.toString(bloqueo().toSeconds());
        Long intentos = RedisNoDisponible.ejecutar(() -> redis.execute(REGISTRAR_FALLO, List.of(clave), segundos));
        return intentos == null ? 0 : intentos;
    }

    private Duration bloqueo() {
        return Duration.ofMinutes(parametros.entero(Parametro.AUTH_LOGIN_WAITING));
    }

    private static long intentos(String guardado) {
        try {
            return Long.parseLong(guardado);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
