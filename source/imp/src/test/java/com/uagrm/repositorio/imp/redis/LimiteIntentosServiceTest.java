package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.LimiteIntentosService.Operacion;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

/**
 * Limite de intentos con un Redis simulado en memoria: el script Lua se emula (INCR + EXPIRE si no hay TTL)
 * para comprobar el comportamiento completo (bloqueo en el intento N+1, TTL siempre puesto, Retry-After).
 */
class LimiteIntentosServiceTest {

    private static final String IP = "203.0.113.7";
    private static final String CLAVE_LOGIN = "auth:rate:login:" + IP;
    private static final int MAXIMO = 10;
    private static final int MINUTOS = 15;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ParametrosService parametros = mock(ParametrosService.class);

    /** Redis en memoria: key -> contador y key -> TTL en segundos. */
    private final Map<String, Long> contadores = new HashMap<>();
    private final Map<String, Long> ttls = new HashMap<>();

    private LimiteIntentosService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(anyString())).thenAnswer(inv -> {
            Long n = contadores.get(inv.<String>getArgument(0));
            return n == null ? null : n.toString();
        });
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(inv -> {
            String clave = inv.getArgument(0);
            return contadores.containsKey(clave) ? ttls.getOrDefault(clave, -1L) : -2L;
        });
        when(redis.execute(eq(LimiteIntentosService.REGISTRAR_FALLO), any(List.class), anyString())).thenAnswer(inv -> {
            String clave = inv.<List<String>>getArgument(1).get(0);
            long n = contadores.merge(clave, 1L, Long::sum);
            if (ttls.getOrDefault(clave, -1L) < 0) {
                ttls.put(clave, Long.parseLong(inv.getArgument(2)));
            }
            return n;
        });
        when(parametros.entero(Parametro.AUTH_LOGIN_INTENTOS)).thenReturn(MAXIMO);
        when(parametros.entero(Parametro.AUTH_LOGIN_WAITING)).thenReturn(MINUTOS);
        service = new LimiteIntentosService(redis, parametros);
    }

    @Test
    void lasKeysSiguenElNamespaceAcordado() {
        assertThat(ClavesRedis.limiteLogin(IP)).isEqualTo(CLAVE_LOGIN);
        assertThat(ClavesRedis.limiteRegistro(IP)).isEqualTo("auth:rate:register:" + IP);
    }

    @Test
    void permiteNIntentosFallidosYBloqueaElSiguienteConRetryAfter() {
        for (int i = 1; i <= MAXIMO; i++) {
            service.verificar(Operacion.LOGIN, IP);  // los intentos 1..10 se procesan
            assertThat(service.registrarFallo(Operacion.LOGIN, IP)).isEqualTo(i);
        }
        ttls.put(CLAVE_LOGIN, 840L);  // pasaron 60 s desde el primer fallo

        assertThatThrownBy(() -> service.verificar(Operacion.LOGIN, IP))   // intento 11
                .isInstanceOfSatisfying(LimiteIntentosException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getRetryAfterSegundos()).isEqualTo(840);
                    assertThat(ex.getMessage()).isEqualTo("Demasiados intentos fallidos. Intente de nuevo en 14 minutos.");
                });
    }

    @Test
    void elPrimerFalloPoneElTtlDeLoginWaitingYLosSiguientesNoLoReinician() {
        service.registrarFallo(Operacion.LOGIN, IP);
        assertThat(ttls.get(CLAVE_LOGIN)).isEqualTo(Duration.ofMinutes(MINUTOS).toSeconds());

        ttls.put(CLAVE_LOGIN, 100L);
        service.registrarFallo(Operacion.LOGIN, IP);
        assertThat(ttls.get(CLAVE_LOGIN)).isEqualTo(100L);  // ventana fija: no se alarga con cada fallo
    }

    @Test
    void sinFallosRegistradosNoBloqueaYLosExitososNoCuentan() {
        for (int i = 0; i < 50; i++) {
            service.verificar(Operacion.LOGIN, IP);  // 50 logins exitosos: nadie llama a registrarFallo
        }
        assertThat(contadores).isEmpty();
        verify(redis, never()).execute(any(), any(List.class), anyString());
    }

    @Test
    void keyQueQuedoSinTtlSeCorrigeAlVerificar() {
        contadores.put(CLAVE_LOGIN, (long) MAXIMO);
        ttls.put(CLAVE_LOGIN, -1L);

        assertThatThrownBy(() -> service.verificar(Operacion.LOGIN, IP))
                .isInstanceOfSatisfying(LimiteIntentosException.class,
                        ex -> assertThat(ex.getRetryAfterSegundos()).isEqualTo(900));
        verify(redis).expire(CLAVE_LOGIN, Duration.ofSeconds(900));
    }

    @Test
    void bajarElLimiteAplicaAlInstante() {
        for (int i = 0; i < 3; i++) {
            service.registrarFallo(Operacion.LOGIN, IP);
        }
        service.verificar(Operacion.LOGIN, IP);

        when(parametros.entero(Parametro.AUTH_LOGIN_INTENTOS)).thenReturn(3);
        assertThatThrownBy(() -> service.verificar(Operacion.LOGIN, IP)).isInstanceOf(LimiteIntentosException.class);
    }

    @Test
    void loginYRegistroTienenContadoresSeparadosYPorIp() {
        contadores.put(CLAVE_LOGIN, (long) MAXIMO);
        ttls.put(CLAVE_LOGIN, 600L);

        service.verificar(Operacion.REGISTRO, IP);
        service.verificar(Operacion.LOGIN, "198.51.100.1");
        assertThatThrownBy(() -> service.verificar(Operacion.LOGIN, IP)).isInstanceOf(LimiteIntentosException.class);
    }

    @Test
    void redisCaidoDevuelve503() {
        when(valores.get(anyString())).thenThrow(new RedisConnectionFailureException("sin conexion"));

        assertThatThrownBy(() -> service.verificar(Operacion.LOGIN, IP))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
