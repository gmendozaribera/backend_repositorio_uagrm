package com.uagrm.repositorio.imp.service.trabajo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.mapper.TrabajoMapperImpl;
import com.uagrm.repositorio.dto.trabajo.AutorResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.mockup.TrabajoMock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Repositorio: ficha publica solo de trabajos EVALUADO (404 para el resto), fecha de publicacion y orden de la
 * busqueda. Los filtros contra PostgreSQL real estan en work-service (RepositorioBusquedaTest, Testcontainers).
 */
class RepositorioServiceTest {

    private final TrabajoRepository trabajoRepository = mock(TrabajoRepository.class);
    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final TrabajoHistorialRepository historialRepository = mock(TrabajoHistorialRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

    private RepositorioService service;

    @BeforeEach
    void setUp() {
        service = new RepositorioService(trabajoRepository, documentoRepository, historialRepository,
                new AccesoTrabajos(usuarioRepository, trabajoRepository, mock(GrupoDocenteRepository.class)),
                new TrabajoMapperImpl());
        for (Usuario u : new Usuario[]{TrabajoMock.autor(), TrabajoMock.otroEstudiante(), TrabajoMock.docente()}) {
            when(usuarioRepository.findConRolByIdUsuario(u.getIdUsuario())).thenReturn(Optional.of(u));
        }
    }

    @Test
    void fichaDeEvaluadoSinCalificacionConAutorActualYFechaDelHistorial() {
        Trabajo trabajo = TrabajoMock.trabajo(EstadoTrabajo.EVALUADO);
        trabajo.setCalificacion(95);
        trabajo.setObservaciones("Muy bien");
        when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(documentoRepository.findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(TrabajoMock.ID_TRABAJO))
                .thenReturn(List.of(TrabajoMock.pdf(trabajo)));
        Instant publicado = Instant.parse("2026-12-01T12:00:00Z");
        when(historialRepository.findUltimaFechaEstado(TrabajoMock.ID_TRABAJO, EstadoTrabajo.EVALUADO))
                .thenReturn(Optional.of(publicado));

        TrabajoPublicoResponse delAutor = service.obtener(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO);
        assertThat(delAutor.fechaPublicacion()).isEqualTo(publicado);
        assertThat(delAutor.autores()).singleElement().extracting(AutorResponse::esUsuarioActual).isEqualTo(true);
        assertThat(delAutor.documentos()).hasSize(1);
        assertThat(delAutor.idFacultad()).isEqualTo(trabajo.getMateria().getFacultad().getIdFacultad());

        TrabajoPublicoResponse deOtro = service.obtener(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), TrabajoMock.ID_TRABAJO);
        assertThat(deOtro.autores()).singleElement().extracting(AutorResponse::esUsuarioActual).isEqualTo(false);
    }

    @Test
    void sinTransicionEnElHistorialUsaLaUltimaActualizacion() {
        Trabajo trabajo = TrabajoMock.trabajo(EstadoTrabajo.EVALUADO);
        when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(historialRepository.findUltimaFechaEstado(any(), any())).thenReturn(Optional.empty());
        assertThat(service.obtener(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO).fechaPublicacion())
                .isEqualTo(trabajo.getFechaActualizacion());
    }

    @Test
    void fichaDeTrabajoNoEvaluadoEs404AunqueSeaDelAutor() {
        for (EstadoTrabajo estado : EstadoTrabajo.values()) {
            if (estado == EstadoTrabajo.EVALUADO) {
                continue;
            }
            when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO))
                    .thenReturn(Optional.of(TrabajoMock.trabajo(estado)));
            assertThatThrownBy(() -> service.obtener(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO))
                    .isInstanceOf(NegocioException.class)
                    .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThatThrownBy(() -> service.obtener(jwt(TrabajoMock.ID_AUTOR), 999))
                .isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void busquedaOrdenadaPorPublicacionDescendente() {
        when(trabajoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(TrabajoMock.trabajo(EstadoTrabajo.EVALUADO))));
        when(historialRepository.findUltimasFechasEstado(List.of(TrabajoMock.ID_TRABAJO), EstadoTrabajo.EVALUADO))
                .thenReturn(List.<Object[]>of(new Object[]{TrabajoMock.ID_TRABAJO, Instant.parse("2026-12-01T12:00:00Z")}));
        var pagina = service.buscar(FiltrosRepositorio.ninguno(), 2, 10);
        assertThat(pagina.contenido()).singleElement().satisfies(t -> {
            assertThat(t.idTrabajo()).isEqualTo(TrabajoMock.ID_TRABAJO);
            assertThat(t.fechaPublicacion()).isEqualTo(Instant.parse("2026-12-01T12:00:00Z"));
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(trabajoRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("fechaActualizacion"), Sort.Order.desc("idTrabajo"));
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }
}
