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
    private static final String CLAVE_SMTP = "config:dominio:" + ConstantesDominio.GRUPO_SMTP;
    private static final CifradorDominio CIFRADOR = new CifradorDominio(CifradorDominioTest.LLAVE);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final DominioRepository repository = mock(DominioRepository.class);

    private ParametrosService service;

    @BeforeEach
    void setUp() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashes);
        when(hashes.entries(CLAVE)).thenReturn(Map.of());
        service = new ParametrosService(redis, repository, new DominioCacheProperties(Duration.ofSeconds(600)), CIFRADOR);
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

    @Test
    void validacionPorTipo() {
        assertThat(Parametro.SMTP_ACTIVO.validar("true")).isEmpty();
        assertThat(Parametro.SMTP_ACTIVO.validar("si")).hasValue("debe ser 'true' o 'false'");
        assertThat(Parametro.SMTP_SEGURIDAD.validar("SSL")).isEmpty();
        assertThat(Parametro.SMTP_SEGURIDAD.validar("TLS")).hasValue("debe ser uno de: SSL, STARTTLS, NINGUNA");
        assertThat(Parametro.SMTP_CORREO.validar("no-responder@uagrm.edu.bo")).isEmpty();
        assertThat(Parametro.SMTP_CORREO.validar("sin-arroba")).isPresent();
        assertThat(Parametro.SMTP_CORREO.validar("")).hasValue("no puede estar vacio");
        assertThat(Parametro.SMTP_HOST.validar("smtp.gmail.com")).isEmpty();
        assertThat(Parametro.SMTP_HOST.validar("10.0.0.5")).isEmpty();
        assertThat(Parametro.SMTP_HOST.validar("")).isEmpty();
        assertThat(Parametro.SMTP_HOST.validar("smtp gmail.com")).isPresent();
        assertThat(Parametro.SMTP_HOST.validar("-malo.com")).isPresent();
        assertThat(Parametro.SMTP_PUERTO.validar("65536")).isPresent();
        assertThat(Parametro.SMTP_PASSWORD.validar("")).isEmpty();
        assertThat(Parametro.SMTP_PASSWORD.validar("x".repeat(ConstantesDominio.MAXIMO_BYTES_SECRETO + 1))).isPresent();
        assertThat(Parametro.FACULTAD_ESCALA_CALIF_MAX.validar("1000")).isEmpty();
        assertThat(Parametro.FACULTAD_ESCALA_CALIF_MAX.validar("1001")).isPresent();
        assertThat(Parametro.FACULTAD_JWT_EXPIRACION_HORAS.validar("25")).isPresent();
        assertThat(ConstantesDominio.esSecreto(ConstantesDominio.GRUPO_SMTP, ConstantesDominio.PASSWORD)).isTrue();
        assertThat(ConstantesDominio.esSecreto(ConstantesDominio.GRUPO_SMTP, ConstantesDominio.HOST)).isFalse();
    }

    @Test
    void elSecretoMasLargoPermitidoCabeCifradoEnDomainValue() {
        // Caracteres de 2 bytes en UTF-8: el limite es en bytes
        String cifrado = CIFRADOR.cifrar("\u00f1".repeat(ConstantesDominio.MAXIMO_BYTES_SECRETO / 2));
        assertThat(cifrado.length()).isLessThanOrEqualTo(500);
    }

    @Test
    void booleanoTextoYSecretoDescifrado() {
        String cifrado = CIFRADOR.cifrar("clave-smtp");
        when(hashes.entries(CLAVE_SMTP)).thenReturn(Map.of(
                ConstantesDominio.ACTIVO, "true",
                ConstantesDominio.SEGURIDAD, "SSL",
                ConstantesDominio.HOST, "",
                ConstantesDominio.PASSWORD, cifrado));

        assertThat(service.booleano(Parametro.SMTP_ACTIVO)).isTrue();
        assertThat(service.texto(Parametro.SMTP_SEGURIDAD)).isEqualTo("SSL");
        assertThat(service.texto(Parametro.SMTP_HOST)).isEmpty();
        assertThat(service.texto(Parametro.SMTP_CORREO)).isEqualTo("no-responder@uagrm.edu.bo");  // falta: default
        assertThat(service.secreto(Parametro.SMTP_PASSWORD)).isEqualTo("clave-smtp");
        // Redis/BD solo tienen el cifrado
        assertThat(service.valor(ConstantesDominio.GRUPO_SMTP, ConstantesDominio.PASSWORD)).hasValue(cifrado);
    }

    @Test
    void secretoSinConfigurarEsVacioYLosAccesosCruzadosSonErrorDeProgramacion() {
        when(hashes.entries(CLAVE_SMTP)).thenReturn(Map.of(ConstantesDominio.PASSWORD, ""));

        assertThat(service.secreto(Parametro.SMTP_PASSWORD)).isEmpty();
        assertThatThrownBy(() -> service.texto(Parametro.SMTP_PASSWORD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.secreto(Parametro.SMTP_HOST)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.booleano(Parametro.SMTP_PUERTO)).isInstanceOf(IllegalArgumentException.class);
    }

    private void bd(String intentos, String espera) {
        when(repository.findByDomainGroupOrderByDomainNameAsc(ConstantesDominio.GRUPO_AUTH)).thenReturn(List.of(
                DominioMock.dominio(1, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_INTENTOS, intentos),
                DominioMock.dominio(2, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_WAITING, espera)));
    }
}
