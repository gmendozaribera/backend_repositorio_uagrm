package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

/**
 * Limite de consultas anonimas por IP con un Redis simulado (el script INCR + EXPIRE se emula en memoria):
 * 60 pasan, la 61 da 429 con Retry-After, contador por IP (IPv6 por /64) y Redis caido deja pasar.
 */
class LimitePublicoServiceTest {

    private static final String IP = "200.87.10.5";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final Map<String, Long> contadores = new HashMap<>();
    private final Map<String, Long> ttls = new HashMap<>();
    private LimitePublicoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(parametros.entero(Parametro.WORK_PUBLICO_PETICIONES_POR_MINUTO)).thenReturn(60);
        when(redis.execute(eq(LimiteIntentosService.REGISTRAR_FALLO), anyList(), anyString())).thenAnswer(inv -> {
            String clave = inv.<List<String>>getArgument(1).getFirst();
            long n = contadores.merge(clave, 1L, Long::sum);
            ttls.putIfAbsent(clave, Long.parseLong(inv.getArgument(2)));
            return n;
        });
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(inv -> ttls.getOrDefault(inv.getArgument(0), -2L));
        service = new LimitePublicoService(redis, parametros);
    }

    @Test
    void sesentaPasanYLaSesentaYUnoDevuelve429ConRetryAfter() {
        for (int i = 0; i < 60; i++) {
            assertThatNoException().isThrownBy(() -> service.registrar(IP));
        }
        assertThat(contadores).containsEntry("work:rate:public:" + IP, 60L);
        assertThat(ttls).containsEntry("work:rate:public:" + IP, 60L);
        ttls.put("work:rate:public:" + IP, 37L);  // pasaron 23 s desde la primera consulta
        assertThatThrownBy(() -> service.registrar(IP))
                .isInstanceOf(LimiteIntentosException.class)
                .hasMessageContaining("60 consultas por minuto")
                .satisfies(ex -> {
                    LimiteIntentosException limite = (LimiteIntentosException) ex;
                    assertThat(limite.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(limite.getRetryAfterSegundos()).isEqualTo(37L);
                });
    }

    @Test
    void elMaximoSaleDeLaTablaDeDominio() {
        when(parametros.entero(Parametro.WORK_PUBLICO_PETICIONES_POR_MINUTO)).thenReturn(2);
        service.registrar(IP);
        service.registrar(IP);
        assertThatThrownBy(() -> service.registrar(IP)).hasMessageContaining("2 consultas por minuto");
    }

    @Test
    void cadaIpTieneSuContadorEIpv6SeAgrupaPorPrefijo64() {
        for (int i = 0; i < 60; i++) {
            service.registrar(IP);
        }
        assertThatNoException().isThrownBy(() -> service.registrar("200.87.10.6"));

        // Mismo /64 con distinto sufijo = mismo visitante (no puede multiplicar su limite cambiando de direccion)
        for (int i = 0; i < 30; i++) {
            service.registrar("2001:db8:1:2::" + Integer.toHexString(i + 1));
            service.registrar("2001:db8:1:2:ffff::" + Integer.toHexString(i + 1));
        }
        assertThat(contadores).containsEntry("work:rate:public:2001:db8:1:2::/64", 60L);
        assertThatThrownBy(() -> service.registrar("2001:db8:1:2:abcd::1")).isInstanceOf(LimiteIntentosException.class);
    }

    @Test
    void sinTtlLeidoSeInformaAlMenosUnSegundo() {
        for (int i = 0; i < 60; i++) {
            service.registrar(IP);
        }
        ttls.put("work:rate:public:" + IP, -2L);  // vencio entre el INCR y el TTL
        assertThatThrownBy(() -> service.registrar(IP))
                .isInstanceOf(LimiteIntentosException.class)
                .extracting("retryAfterSegundos").isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisCaidoDejaPasarSinLeerElMaximo() {
        when(redis.execute(eq(LimiteIntentosService.REGISTRAR_FALLO), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("caido"));
        for (int i = 0; i < 100; i++) {
            assertThatNoException().isThrownBy(() -> service.registrar(IP));
        }
        verifyNoInteractions(parametros);
    }

    @Test
    void redisLentoAlLeerElTtlTambienDejaPasar() {
        for (int i = 0; i < 60; i++) {
            service.registrar(IP);
        }
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenThrow(new QueryTimeoutException("timeout"));
        assertThatNoException().isThrownBy(() -> service.registrar(IP));
    }
}
