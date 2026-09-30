package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Blacklist JWT en Redis. Dos mecanismos:
 * <ul>
 *   <li>auth:blacklist:{jti}: revoca un token puntual (logout, rotacion de refresh).</li>
 *   <li>auth:revocado-desde:{idUsuario} (epoch ms): revoca todos los tokens emitidos antes de un instante (cambio de contrasena).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RevocacionTokenService {

    private static final String REVOCADO = "revoked";

    private final StringRedisTemplate redis;
    private final SeguridadProperties props;

    public void revocar(Jwt token) {
        Duration restante = Duration.between(Instant.now(), token.getExpiresAt());
        if (restante.isNegative() || restante.isZero()) {
            return; // ya expiro solo, no hace falta recordarlo
        }
        RedisNoDisponible.ejecutar(() -> redis.opsForValue().set(ClavesRedis.blacklist(token.getId()), REVOCADO, restante));
    }

    public void revocarTodosDesde(Integer idUsuario, Instant desde) {
        RedisNoDisponible.ejecutar(() -> redis.opsForValue().set(
                ClavesRedis.revocadoDesde(idUsuario.toString()),
                Long.toString(desde.toEpochMilli()),
                props.jwt().refreshTtl()));
    }

    /** Una sola ida a Redis (MGET) por request. */
    public boolean estaRevocado(Jwt token) {
        List<String> valores = RedisNoDisponible.ejecutar(() -> redis.opsForValue().multiGet(List.of(
                ClavesRedis.blacklist(token.getId()),
                ClavesRedis.revocadoDesde(token.getSubject()))));
        if (valores.get(0) != null) {
            return true;
        }
        String revocadoDesde = valores.get(1);
        return revocadoDesde != null && emitidoMs(token) < Long.parseLong(revocadoDesde);
    }

    private static long emitidoMs(Jwt token) {
        Object ms = token.getClaims().get(JwtService.CLAIM_EMITIDO_MS);
        return ms instanceof Number n ? n.longValue() : token.getIssuedAt().toEpochMilli();
    }
}
