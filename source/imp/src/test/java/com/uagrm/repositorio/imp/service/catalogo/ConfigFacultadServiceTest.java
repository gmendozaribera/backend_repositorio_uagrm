package com.uagrm.repositorio.imp.service.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.mapper.ConfigFacultadMapperImpl;
import com.uagrm.repositorio.imp.config.ConfigFacultadCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.ConfigFacultadMock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class ConfigFacultadServiceTest {

    private static final Duration TTL = Duration.ofSeconds(600);
    private static final Integer ID = 1;
    private static final String CLAVE = "config:facultad:1";

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ConfigFacultadRepository repository = mock(ConfigFacultadRepository.class);
    private final FacultadRepository facultadRepository = mock(FacultadRepository.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private ConfigFacultadService service;
    private Facultad facultad;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        service = new ConfigFacultadService(repository, facultadRepository, new ConfigFacultadMapperImpl(), parametros,
                redis, json, new ConfigFacultadCacheProperties(TTL));
        facultad = CatalogoMock.facultad();
        when(facultadRepository.findById(ID)).thenReturn(Optional.of(facultad));
        when(facultadRepository.existsById(ID)).thenReturn(true);
        when(repository.findByFacultadIdFacultad(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        defaultsDeDominio(30, 50);
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void laKeySigueElNamespaceAcordado() {
        assertThat(ClavesRedis.configFacultad(ID)).isEqualTo(CLAVE);
    }

    @Test
    void sinConfiguracionPropiaDevuelveLosDefaultsDeDominioSinCachearlos() {
        ConfigFacultadResponse config = service.obtener(ID);

        assertThat(config).isEqualTo(ConfigFacultadMock.porDefecto(ID));
        assertThat(config.personalizada()).isFalse();
        verify(valores, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void facultadInexistenteDevuelve404() {
        assertThatThrownBy(() -> service.obtener(99)).isInstanceOfSatisfying(NegocioException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void configDeSinFacultadDevuelveLosDefaults() {
        assertThat(service.configDe(null)).isEqualTo(ConfigFacultadMock.porDefecto(null));
        verify(repository, never()).findByFacultadIdFacultad(any());
    }

    @Test
    void personalizadaSeCacheaConTtlYElHitNoConsultaPostgres() throws Exception {
        when(repository.findByFacultadIdFacultad(ID)).thenReturn(Optional.of(ConfigFacultadMock.entidad(facultad, 8)));

        ConfigFacultadResponse config = service.configDe(ID);

        assertThat(config.personalizada()).isTrue();
        assertThat(config.jwtExpiracionHoras()).isEqualTo(8);
        verify(valores).set(eq(CLAVE), anyString(), eq(TTL));

        // Hit
        when(valores.get(CLAVE)).thenReturn(json.writeValueAsString(ConfigFacultadMock.personalizada(ID, 5)));
        assertThat(service.configDe(ID).jwtExpiracionHoras()).isEqualTo(5);
    }

    @Test
    void redisCaidoDegradaAPostgres() {
        when(valores.get(CLAVE)).thenThrow(new RedisConnectionFailureException("sin conexion"));
        when(repository.findByFacultadIdFacultad(ID)).thenReturn(Optional.of(ConfigFacultadMock.entidad(facultad, 6)));

        assertThat(service.configDe(ID).jwtExpiracionHoras()).isEqualTo(6);
    }

    @Test
    void putCreaLaConfiguracionEInvalidaLaCacheDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        ConfigFacultadResponse guardada = service.actualizar(ID, ConfigFacultadMock.request(25, 60));

        assertThat(guardada.personalizada()).isTrue();
        assertThat(guardada.idFacultad()).isEqualTo(ID);
        assertThat(guardada.jwtExpiracionHoras()).isEqualTo(8);
        verify(repository).save(any(ConfigFacultad.class));
        verify(redis, never()).delete(anyString());

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        verify(redis).delete(CLAVE);
    }

    @Test
    void putActualizaLaFilaExistente() {
        ConfigFacultad existente = ConfigFacultadMock.entidad(facultad, 2);
        when(repository.findByFacultadIdFacultad(ID)).thenReturn(Optional.of(existente));

        service.actualizar(ID, ConfigFacultadMock.request(10, 90));

        assertThat(existente.getUmbralPlagioMedio()).isEqualTo(10);
        assertThat(existente.getUmbralPlagioAlto()).isEqualTo(90);
        assertThat(existente.getJwtExpiracionHoras()).isEqualTo(8);
        verify(repository).save(existente);
    }

    @Test
    void umbralMedioDebeSerMenorQueElAlto() {
        assertThatThrownBy(() -> service.actualizar(ID, ConfigFacultadMock.request(50, 50)))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrores()).isEqualTo(Map.of("umbralPlagioMedio", "debe ser menor que umbralPlagioAlto"));
                });
        verify(repository, never()).save(any());
    }

    @Test
    void putDeFacultadInexistenteDevuelve404() {
        assertThatThrownBy(() -> service.actualizar(99, ConfigFacultadMock.request(25, 60)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void umbralesPorDefectoIncoherentesUsanLosDeConstantes() {
        defaultsDeDominio(70, 40);

        ConfigFacultadResponse config = service.configDe(ID);

        assertThat(config.umbralPlagioMedio()).isEqualTo(30);
        assertThat(config.umbralPlagioAlto()).isEqualTo(50);
    }

    private void defaultsDeDominio(int medio, int alto) {
        when(parametros.entero(Parametro.FACULTAD_TAMANO_MAX_ARCHIVO_MB)).thenReturn(10);
        when(parametros.entero(Parametro.FACULTAD_UMBRAL_PLAGIO_MEDIO)).thenReturn(medio);
        when(parametros.entero(Parametro.FACULTAD_UMBRAL_PLAGIO_ALTO)).thenReturn(alto);
        when(parametros.booleano(Parametro.FACULTAD_COMPARACION_INTERNET)).thenReturn(false);
        when(parametros.booleano(Parametro.FACULTAD_ACCESO_EXTERNO)).thenReturn(false);
        when(parametros.entero(Parametro.FACULTAD_ESCALA_CALIF_MAX)).thenReturn(100);
        when(parametros.entero(Parametro.FACULTAD_JWT_EXPIRACION_HORAS)).thenReturn(1);
    }
}
