package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sesion 2FA pendiente entre el paso 1 (password correcta) y el paso 2 (codigo TOTP) del login.
 * La key usa un token aleatorio, no el idUsuario, para que no se pueda adivinar ni atacar por fuerza bruta.
 */
@Service
@RequiredArgsConstructor
public class DosFactoresPendienteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SeguridadProperties props;

    public String crear(Integer idUsuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RedisNoDisponible.ejecutar(() -> redis.opsForValue().set(
                ClavesRedis.dosFactoresPendiente(token), idUsuario.toString(), props.dosFactores().ttlPendiente()));
        return token;
    }

    /**
     * Registra un intento y devuelve el idUsuario de la sesion pendiente.
     * Al superar el maximo de intentos la sesion se anula y el usuario debe volver a ingresar su contrasena.
     */
    public Integer registrarIntento(String token) {
        String claveSesion = ClavesRedis.dosFactoresPendiente(token);
        String claveIntentos = ClavesRedis.dosFactoresIntentos(token);

        String idUsuario = RedisNoDisponible.ejecutar(() -> redis.opsForValue().get(claveSesion));
        if (idUsuario == null) {
            throw NegocioException.noAutorizado("La verificacion expiro o no existe. Inicie sesion nuevamente.");
        }

        Long intentos = RedisNoDisponible.ejecutar(() -> redis.opsForValue().increment(claveIntentos));
        if (intentos != null && intentos == 1) {
            RedisNoDisponible.ejecutar(() -> redis.expire(claveIntentos, props.dosFactores().ttlPendiente()));
        }
        if (intentos != null && intentos > props.dosFactores().maxIntentos()) {
            eliminar(token);
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
                    "Se supero el numero de intentos permitidos. Inicie sesion nuevamente.");
        }
        return Integer.valueOf(idUsuario);
    }

    public void eliminar(String token) {
        RedisNoDisponible.ejecutar(() -> redis.delete(List.of(
                ClavesRedis.dosFactoresPendiente(token), ClavesRedis.dosFactoresIntentos(token))));
    }
}
