package com.uagrm.repositorio.imp.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.repository.DominioRepository;
import com.uagrm.repositorio.imp.config.DominioCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import com.uagrm.repositorio.mockup.DominioMock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ParametrosServiceTest {

    private static final String CLAVE = "config:dominio:" + ConstantesDominio.GRUPO_AUTH;

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final DominioRepository repository = mock(DominioRepository.class);

    private ParametrosService service;

    @BeforeEach
    void setUp() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashes);
        when(hashes.entries(CLAVE)).thenReturn(Map.of());
        service = new ParametrosService(redis, repository, new DominioCacheProperties(Duration.ofSeconds(600)));
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void laKeySigueElNamespaceAcordado() {
        assertThat(ClavesRedis.dominio(ConstantesDominio.GRUPO_AUTH)).isEqualTo(CLAVE);
    }

    @Test
    void hitLeeElHashSinConsultarPostgres() {
        when(hashes.entries(CLAVE)).thenReturn(Map.of(ConstantesDominio.LOGIN_INTENTOS, "7"));

        assertThat(service.entero(Parametro.AUTH_LOGIN_INTENTOS)).isEqualTo(7);
        verify(repository, never()).findByDomainGroupOrderByDomainNameAsc(any());
    }

    @Test
    void missCargaTodoElGrupoYLoGuardaConTtlEnUnSoloScript() {
        bd("12", "30");

        assertThat(service.entero(ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_INTENTOS)).isEqualTo(12);

        // ARGV = TTL, luego pares nombre/valor de TODO el grupo (DEL + HSET + EXPIRE atomico)
        verify(redis).execute(ParametrosService.GUARDAR_GRUPO, List.of(CLAVE), "600",
                ConstantesDominio.LOGIN_INTENTOS, "12", ConstantesDominio.LOGIN_WAITING, "30");
    }

    @Test
    void redisCaidoDegradaAPostgres() {
        when(hashes.entries(CLAVE)).thenThrow(new RedisConnectionFailureException("sin conexion"));
        bd("12", "30");

        assertThat(service.entero(Parametro.AUTH_LOGIN_WAITING)).isEqualTo(30);
    }

    @Test
    void parametroInexistenteOInvalidoUsaElValorPorDefecto() {
        when(repository.findByDomainGroupOrderByDomainNameAsc(ConstantesDominio.GRUPO_AUTH)).thenReturn(List.of());
        assertThat(service.entero(Parametro.AUTH_LOGIN_INTENTOS)).isEqualTo(10);
        verify(redis, never()).execute(any(), anyList(), any(Object[].class));

        when(hashes.entries(CLAVE)).thenReturn(Map.of(
                ConstantesDominio.LOGIN_INTENTOS, "muchos", ConstantesDominio.LOGIN_WAITING, "99999"));
        assertThat(service.entero(Parametro.AUTH_LOGIN_INTENTOS)).isEqualTo(10);
        assertThat(service.entero(Parametro.AUTH_LOGIN_WAITING)).isEqualTo(15);
    }

    @Test
    void parametroNoDeclaradoEnConstantesEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> service.entero(ConstantesDominio.GRUPO_AUTH, "NO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidarDentroDeUnaTransaccionBorraRecienDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.invalidar(ConstantesDominio.GRUPO_AUTH);
        verify(redis, never()).delete(anyString());

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        verify(redis).delete(CLAVE);
    }

    @Test
    void invalidarConRedisCaidoNoRompeLaEscritura() {
        when(redis.delete(CLAVE)).thenThrow(new RedisConnectionFailureException("sin conexion"));

        service.invalidar(ConstantesDominio.GRUPO_AUTH);

        verify(redis).delete(CLAVE);
    }

    @Test
    void validacionDeTipoYRango() {
        assertThat(Parametro.AUTH_LOGIN_INTENTOS.validar("1")).isEmpty();
        assertThat(Parametro.AUTH_LOGIN_INTENTOS.validar("1000")).isEmpty();
        assertThat(Parametro.AUTH_LOGIN_INTENTOS.validar("0")).isPresent();
        assertThat(Parametro.AUTH_LOGIN_INTENTOS.validar("1001")).isPresent();
        assertThat(Parametro.AUTH_LOGIN_INTENTOS.validar("diez")).isPresent();
        assertThat(Parametro.AUTH_LOGIN_WAITING.validar("1440")).isEmpty();
        assertThat(Parametro.AUTH_LOGIN_WAITING.validar("1441"))
                .hasValue("debe ser un numero entero entre 1 y 1440");
        assertThat(ConstantesDominio.esDelSistema(ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_WAITING)).isTrue();
        assertThat(ConstantesDominio.esDelSistema("UI_GROUP", "TEMA")).isFalse();
    }

    private void bd(String intentos, String espera) {
        when(repository.findByDomainGroupOrderByDomainNameAsc(ConstantesDominio.GRUPO_AUTH)).thenReturn(List.of(
                DominioMock.dominio(1, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_INTENTOS, intentos),
                DominioMock.dominio(2, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_WAITING, espera)));
    }
}
