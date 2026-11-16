package com.uagrm.repositorio.imp.service.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dto.catalogo.FacultadRequest;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapperImpl;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import com.uagrm.repositorio.mockup.CatalogoMock;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class FacultadServiceTest {

    private final FacultadRepository facultadRepository = mock(FacultadRepository.class);
    private final CatalogoCacheService cache = mock(CatalogoCacheService.class);
    private FacultadService service;

    @BeforeEach
    void setUp() {
        service = new FacultadService(facultadRepository, new CatalogoMapperImpl(), cache);
        when(facultadRepository.save(any(Facultad.class))).thenAnswer(inv -> {
            Facultad f = inv.getArgument(0);
            if (f.getIdFacultad() == null) {
                f.setIdFacultad(7);
            }
            return f;
        });
    }

    @Test
    void listarActivasPasaPorLaCache() {
        when(cache.listar(eq(EntidadCatalogo.FACULTADES), eq(null), eq(FacultadResponse.class), any()))
                .thenReturn(List.of(CatalogoMock.facultadResponse()));

        assertThat(service.listar(false)).hasSize(1);
        verify(facultadRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missDeCacheConsultaPostgres() {
        when(cache.listar(eq(EntidadCatalogo.FACULTADES), eq(null), eq(FacultadResponse.class), any()))
                .thenAnswer(inv -> ((Supplier<List<FacultadResponse>>) inv.getArgument(3)).get());
        when(facultadRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(CatalogoMock.facultad()));

        assertThat(service.listar(false)).extracting(FacultadResponse::sigla).containsExactly("FICCT");
    }

    @Test
    void conInactivosNoUsaLaCache() {
        when(facultadRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(CatalogoMock.facultad()));

        assertThat(service.listar(true)).hasSize(1);
        verify(cache, never()).listar(any(), any(), any(), any());
    }

    @Test
    void crearNormalizaLaSiglaEInvalidaLaCache() {
        FacultadResponse creada = service.crear(new FacultadRequest("  Facultad de Tecnologia ", " fct ", null));

        assertThat(creada.sigla()).isEqualTo("FCT");
        assertThat(creada.nombre()).isEqualTo("Facultad de Tecnologia");
        assertThat(creada.estado()).isEqualTo(Estado.ACTIVO);
        verify(cache).invalidar(EntidadCatalogo.FACULTADES);
    }

    @Test
    void siglaDuplicadaDevuelve409SinGuardar() {
        when(facultadRepository.existsBySiglaAndIdFacultadNot("FICCT", 0)).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new FacultadRequest("Otra", "ficct", null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(facultadRepository, never()).save(any());
        verify(cache, never()).invalidar(any());
    }

    @Test
    void actualizarSinEstadoConservaElActualYReactivarConEstado1() {
        Facultad facultad = CatalogoMock.facultad();
        facultad.setEstado(Estado.INACTIVO);
        when(facultadRepository.findById(1)).thenReturn(Optional.of(facultad));

        assertThat(service.actualizar(1, new FacultadRequest("FICCT", "FICCT", null)).estado()).isEqualTo(Estado.INACTIVO);
        assertThat(service.actualizar(1, new FacultadRequest("FICCT", "FICCT", Estado.ACTIVO)).estado())
                .isEqualTo(Estado.ACTIVO);
    }

    @Test
    void desactivarEsBorradoLogico() {
        Facultad facultad = CatalogoMock.facultad();
        when(facultadRepository.findById(1)).thenReturn(Optional.of(facultad));

        assertThat(service.desactivar(1).estado()).isEqualTo(Estado.INACTIVO);
        verify(facultadRepository, never()).delete(any(Facultad.class));
        verify(cache).invalidar(EntidadCatalogo.FACULTADES);
    }

    @Test
    void facultadInexistenteDevuelve404() {
        when(facultadRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar(99))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
