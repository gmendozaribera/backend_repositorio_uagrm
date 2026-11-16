package com.uagrm.repositorio.imp.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.imp.config.CatalogoCacheProperties;
import com.uagrm.repositorio.mockup.CatalogoMock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class CatalogoCacheServiceTest {

    private static final Duration TTL = Duration.ofSeconds(600);
    private static final String CLAVE = "catalog:cache:carreras:facultad:1";
    private static final String INDICE = "catalog:cache:carreras:indice";

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> conjuntos = mock(SetOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private CatalogoCacheService cache;
    private final AtomicInteger consultasBd = new AtomicInteger();
    private final List<CarreraResponse> enBd = List.of(CatalogoMock.carreraResponse());

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        when(redis.opsForSet()).thenReturn(conjuntos);
        cache = new CatalogoCacheService(redis, json, new CatalogoCacheProperties(TTL));
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void lasKeysSiguenElNamespaceAcordado() {
        assertThat(ClavesRedis.catalogo("carreras", 1)).isEqualTo(CLAVE);
        assertThat(ClavesRedis.catalogo("facultades", null)).isEqualTo("catalog:cache:facultades:todas");
        assertThat(ClavesRedis.catalogoIndice("carreras")).isEqualTo(INDICE);
    }

    @Test
    void hitDevuelveLoCacheadoSinConsultarPostgres() {
        when(valores.get(CLAVE)).thenReturn(json.writeValueAsString(enBd));

        List<CarreraResponse> resultado = listarCarreras();

        assertThat(resultado).containsExactlyElementsOf(enBd);
        assertThat(consultasBd).hasValue(0);
        verify(valores, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void missConsultaPostgresYGuardaConTtlEIndice() {
        List<CarreraResponse> resultado = listarCarreras();

        assertThat(resultado).isEqualTo(enBd);
        assertThat(consultasBd).hasValue(1);
        verify(valores).set(CLAVE, json.writeValueAsString(enBd), TTL);
        verify(conjuntos).add(INDICE, CLAVE);
        verify(redis).expire(INDICE, TTL);
    }

    @Test
    void redisCaidoDegradaAPostgresSinError() {
        when(valores.get(CLAVE)).thenThrow(new RedisConnectionFailureException("sin conexion"));
        when(conjuntos.add(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("sin conexion"));

        List<CarreraResponse> resultado = listarCarreras();

        assertThat(resultado).isEqualTo(enBd);
        assertThat(consultasBd).hasValue(1);
    }

    @Test
    void entradaCorruptaSeTrataComoMiss() {
        when(valores.get(CLAVE)).thenReturn("{no es json");

        assertThat(listarCarreras()).isEqualTo(enBd);
        assertThat(consultasBd).hasValue(1);
    }

    @Test
    void invalidarBorraTodasLasKeysDeLaEntidadYSuIndice() {
        when(conjuntos.members(INDICE)).thenReturn(Set.of(CLAVE));

        cache.invalidar(EntidadCatalogo.CARRERAS);

        verify(redis).delete(List.of(CLAVE, INDICE));
        verify(redis, never()).keys(anyString());
    }

    @Test
    void invalidarConRedisCaidoNoRompeLaEscritura() {
        when(conjuntos.members(INDICE)).thenThrow(new RedisConnectionFailureException("sin conexion"));

        cache.invalidar(EntidadCatalogo.CARRERAS);

        verify(redis, never()).delete(any(List.class));
    }

    @Test
    void dentroDeUnaTransaccionInvalidaRecienDespuesDelCommit() {
        when(conjuntos.members(INDICE)).thenReturn(Set.of(CLAVE));
        TransactionSynchronizationManager.initSynchronization();

        cache.invalidar(EntidadCatalogo.CARRERAS);
        verify(redis, never()).delete(any(List.class));

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        verify(redis).delete(eq(List.of(CLAVE, INDICE)));
    }

    private List<CarreraResponse> listarCarreras() {
        return cache.listar(EntidadCatalogo.CARRERAS, 1, CarreraResponse.class, () -> {
            consultasBd.incrementAndGet();
            return enBd;
        });
    }
}
