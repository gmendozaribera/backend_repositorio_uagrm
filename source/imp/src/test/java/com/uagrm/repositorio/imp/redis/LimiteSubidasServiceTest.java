package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

/** Limite de subidas por usuario con un Redis simulado (el script INCR + EXPIRE se emula en memoria). */
class LimiteSubidasServiceTest {

    private static final int USUARIO = 33;
    private static final String CLAVE = "work:rate:upload:33";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final Map<String, Long> contadores = new HashMap<>();
    private final Map<String, Long> ttls = new HashMap<>();
    private LimiteSubidasService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(parametros.entero(Parametro.WORK_UPLOADS_POR_MINUTO)).thenReturn(10);
        when(redis.execute(eq(LimiteIntentosService.REGISTRAR_FALLO), anyList(), anyString())).thenAnswer(inv -> {
            String clave = inv.<List<String>>getArgument(1).getFirst();
            long n = contadores.merge(clave, 1L, Long::sum);
            ttls.putIfAbsent(clave, Long.parseLong(inv.getArgument(2)));
            return n;
        });
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(inv -> ttls.getOrDefault(inv.getArgument(0), -2L));
        service = new LimiteSubidasService(redis, parametros);
    }

    @Test
    void diezSubidasPasanYLaOnceDevuelve429ConRetryAfter() {
        for (int i = 0; i < 10; i++) {
            assertThatNoException().isThrownBy(() -> service.registrar(USUARIO));
        }
        assertThat(ttls).containsEntry(CLAVE, 60L);
        ttls.put(CLAVE, 42L);  // pasaron 18 s desde la primera subida
        assertThatThrownBy(() -> service.registrar(USUARIO))
                .isInstanceOf(LimiteIntentosException.class)
                .hasMessageContaining("10 subidas por minuto")
                .satisfies(ex -> {
                    LimiteIntentosException limite = (LimiteIntentosException) ex;
                    assertThat(limite.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(limite.getRetryAfterSegundos()).isEqualTo(42L);
                });
    }

    @Test
    void cadaUsuarioTieneSuContador() {
        for (int i = 0; i < 10; i++) {
            service.registrar(USUARIO);
        }
        assertThatNoException().isThrownBy(() -> service.registrar(34));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisCaidoDevuelve503() {
        when(redis.execute(eq(LimiteIntentosService.REGISTRAR_FALLO), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("caido"));
        assertThatThrownBy(() -> service.registrar(USUARIO))
                .isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
