package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dto.plagio.EstadoPlagioCache;
import com.uagrm.repositorio.imp.config.PlagioCacheProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

/** Cache plagio:status:{idTrabajo}: JSON corto con TTL 600 s; Redis caido o dato ilegible = "no hay cache". */
class EstadoPlagioCacheServiceTest {

    private static final Duration TTL = Duration.ofSeconds(600);
    private static final String CLAVE = "plagio:status:7";

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private EstadoPlagioCacheService cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        cache = new EstadoPlagioCacheService(redis, JsonMapper.builder().build(), new PlagioCacheProperties(TTL));
    }

    @Test
    void guardaJsonCortoConTtlYLoLeeDeVuelta() {
        EstadoPlagioCache estado = new EstadoPlagioCache(90, EstadoAnalisisPlagio.COMPLETADO, 34.2, NivelPlagio.MEDIO);
        cache.guardar(7, estado);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq(CLAVE), json.capture(), eq(TTL));
        assertThat(json.getValue()).isEqualTo(
                "{\"idAnalisis\":90,\"estado\":\"COMPLETADO\",\"porcentaje\":34.2,\"nivel\":\"MEDIO\"}");

        when(valores.get(CLAVE)).thenReturn(json.getValue());
        assertThat(cache.leer(7)).contains(estado);
    }

    @Test
    void missOIlegibleDevuelveVacio() {
        assertThat(cache.leer(7)).isEmpty();
        when(valores.get(CLAVE)).thenReturn("{no es json");
        assertThat(cache.leer(7)).isEmpty();
    }

    @Test
    void redisCaidoNoFalla() {
        when(valores.get(anyString())).thenThrow(new RedisConnectionFailureException("caido"));
        assertThat(cache.leer(7)).isEmpty();

        doThrow(new RedisConnectionFailureException("caido")).when(valores).set(anyString(), anyString(), eq(TTL));
        cache.guardar(7, new EstadoPlagioCache(90, EstadoAnalisisPlagio.EN_PROCESO, null, null));
        verify(redis).delete(CLAVE);  // intenta no dejar un estado viejo
    }
}
