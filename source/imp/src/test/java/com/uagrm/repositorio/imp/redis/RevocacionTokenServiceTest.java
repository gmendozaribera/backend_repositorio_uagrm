package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class RevocacionTokenServiceTest {

    private static final Instant EMITIDO = Instant.parse("2026-09-01T10:00:00Z");

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private RevocacionTokenService service;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valores);
        SeguridadProperties props = new SeguridadProperties(
                new SeguridadProperties.Jwt("x", "test", Duration.ofHours(1), Duration.ofDays(7)),
                new SeguridadProperties.DosFactores("Test", Duration.ofMinutes(5), 5),
                List.of("http://localhost:4200"));
        service = new RevocacionTokenService(redis, props);
    }

    @Test
    void tokenSinEntradasEnRedisEsValido() {
        when(valores.multiGet(anyList())).thenReturn(Arrays.asList(null, null));
        assertThat(service.estaRevocado(token())).isFalse();
    }

    @Test
    void tokenEnBlacklistEstaRevocado() {
        when(valores.multiGet(anyList())).thenReturn(Arrays.asList("revoked", null));
        assertThat(service.estaRevocado(token())).isTrue();
    }

    @Test
    void tokenEmitidoAntesDelCambioDePasswordEstaRevocado() {
        String cambio = Long.toString(EMITIDO.plusSeconds(60).toEpochMilli());
        when(valores.multiGet(anyList())).thenReturn(Arrays.asList(null, cambio));
        assertThat(service.estaRevocado(token())).isTrue();
    }

    @Test
    void tokenEmitidoMilisegundosAntesDelCambioEnElMismoSegundoEstaRevocado() {
        // Caso real: login y cambio de password dentro del mismo segundo (iat identico)
        String cambio = Long.toString(EMITIDO.plusMillis(300).toEpochMilli());
        when(valores.multiGet(anyList())).thenReturn(Arrays.asList(null, cambio));
        assertThat(service.estaRevocado(tokenConMs(EMITIDO.plusMillis(100)))).isTrue();
    }

    @Test
    void tokenEmitidoDespuesDelCambioSigueValido() {
        // Los tokens nuevos que devuelve /password/change se emiten justo despues de revocar
        String cambio = Long.toString(EMITIDO.plusMillis(300).toEpochMilli());
        when(valores.multiGet(anyList())).thenReturn(Arrays.asList(null, cambio));
        assertThat(service.estaRevocado(tokenConMs(EMITIDO.plusMillis(301)))).isFalse();
    }

    @Test
    void siRedisNoRespondeSeRechazaCon503() {
        when(valores.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("caido"));
        assertThatThrownBy(() -> service.estaRevocado(token()))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static Jwt tokenConMs(Instant emitido) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .jti("jti-1")
                .subject("1")
                .issuedAt(EMITIDO)
                .expiresAt(EMITIDO.plus(Duration.ofHours(1)))
                .claim(JwtService.CLAIM_EMITIDO_MS, emitido.toEpochMilli())
                .build();
    }

    private static Jwt token() {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .jti("jti-1")
                .subject("1")
                .issuedAt(EMITIDO)
                .expiresAt(EMITIDO.plus(Duration.ofHours(1)))
                .build();
    }
}
