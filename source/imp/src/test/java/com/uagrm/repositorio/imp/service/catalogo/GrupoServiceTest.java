package com.uagrm.repositorio.imp.service.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.GrupoDocenteId;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.GrupoRepository;
import com.uagrm.repositorio.dao.repository.MateriaRepository;
import com.uagrm.repositorio.dao.repository.SemestreRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.catalogo.ActualizarDocenteGrupoRequest;
import com.uagrm.repositorio.dto.catalogo.AsignarDocenteRequest;
import com.uagrm.repositorio.dto.catalogo.DocenteGrupoResponse;
import com.uagrm.repositorio.dto.catalogo.GrupoRequest;
import com.uagrm.repositorio.dto.catalogo.GrupoResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapperImpl;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GrupoServiceTest {

    private final GrupoRepository grupoRepository = mock(GrupoRepository.class);
    private final GrupoDocenteRepository grupoDocenteRepository = mock(GrupoDocenteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final MateriaRepository materiaRepository = mock(MateriaRepository.class);
    private final SemestreRepository semestreRepository = mock(SemestreRepository.class);

    private GrupoService service;
    private Materia materia;
    private Semestre semestre;
    private Grupo grupo;
    private Usuario docente;

    @BeforeEach
    void setUp() {
        ReferenciasCatalogo referencias = new ReferenciasCatalogo(
                mock(FacultadRepository.class), materiaRepository, semestreRepository);
        service = new GrupoService(grupoRepository, grupoDocenteRepository, usuarioRepository, referencias,
                new CatalogoMapperImpl());

        Facultad ficct = CatalogoMock.facultad();
        materia = CatalogoMock.materia(ficct);
        semestre = CatalogoMock.semestre(ficct);
        grupo = CatalogoMock.grupo(materia, semestre);
        docente = CatalogoMock.docente();

        when(materiaRepository.findById(20)).thenReturn(Optional.of(materia));
        when(semestreRepository.findById(30)).thenReturn(Optional.of(semestre));
        when(grupoRepository.findById(40)).thenReturn(Optional.of(grupo));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findConRolByIdUsuario(5)).thenReturn(Optional.of(docente));
        when(grupoDocenteRepository.save(any(GrupoDocente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearGrupoConMateriaYSemestreDeLaMismaFacultad() {
        GrupoResponse creado = service.crear(new GrupoRequest(" sb ", 20, 30, null));

        assertThat(creado.nombre()).isEqualTo("sb");
        assertThat(creado.idMateria()).isEqualTo(20);
        assertThat(creado.idSemestre()).isEqualTo(30);
        assertThat(creado.estado()).isEqualTo(Estado.ACTIVO);
    }

    @Test
    void materiaYSemestreDeFacultadesDistintasDevuelve400() {
        semestre.setFacultad(CatalogoMock.facultad(2, "FCET"));

        assertThatThrownBy(() -> service.crear(new GrupoRequest("SA", 20, 30, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(grupoRepository, never()).save(any());
    }

    @Test
    void grupoRepetidoEnMateriaYSemestreDevuelve409() {
        when(grupoRepository.existsByMateriaIdMateriaAndSemestreIdSemestreAndNombreAndIdGrupoNot(20, 30, "SA", 0))
                .thenReturn(true);

        assertThatThrownBy(() -> service.crear(new GrupoRequest("SA", 20, 30, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void asignarDocenteComoAdminDeCurso() {
        DocenteGrupoResponse asignado = service.asignarDocente(40, new AsignarDocenteRequest(5, true));

        assertThat(asignado.idGrupo()).isEqualTo(40);
        assertThat(asignado.idUsuario()).isEqualTo(5);
        assertThat(asignado.usuario()).isEqualTo("doc.maria");
        assertThat(asignado.esAdminCurso()).isTrue();
    }

    @Test
    void asignarUnUsuarioQueNoEsDocenteDevuelve400() {
        when(usuarioRepository.findConRolByIdUsuario(1)).thenReturn(Optional.of(UsuarioMock.estudiante()));

        assertThatThrownBy(() -> service.asignarDocente(40, new AsignarDocenteRequest(1, false)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(grupoDocenteRepository, never()).save(any());
    }

    @Test
    void asignarDocenteYaAsignadoDevuelve409() {
        when(grupoDocenteRepository.existsById(new GrupoDocenteId(40, 5))).thenReturn(true);

        assertThatThrownBy(() -> service.asignarDocente(40, new AsignarDocenteRequest(5, false)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void asignarDocenteAGrupoInactivoDevuelve409() {
        grupo.setEstado(Estado.INACTIVO);

        assertThatThrownBy(() -> service.asignarDocente(40, new AsignarDocenteRequest(5, false)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void quitarDocenteNoAsignadoDevuelve404() {
        assertThatThrownBy(() -> service.quitarDocente(40, 5))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(grupoDocenteRepository, never()).deleteById(any());
    }

    @Test
    void quitarDocenteAsignado() {
        GrupoDocenteId id = new GrupoDocenteId(40, 5);
        when(grupoDocenteRepository.existsById(id)).thenReturn(true);

        service.quitarDocente(40, 5);

        verify(grupoDocenteRepository).deleteById(id);
    }

    @Test
    void cambiarEsAdminCursoDeDocenteAsignado() {
        GrupoDocente asignacion = new GrupoDocente();
        asignacion.setId(new GrupoDocenteId(40, 5));
        asignacion.setGrupo(grupo);
        asignacion.setUsuario(docente);
        asignacion.setEsAdminCurso(false);
        when(grupoDocenteRepository.findById(new GrupoDocenteId(40, 5))).thenReturn(Optional.of(asignacion));

        DocenteGrupoResponse actualizado = service.actualizarDocente(40, 5, new ActualizarDocenteGrupoRequest(true));

        assertThat(actualizado.esAdminCurso()).isTrue();
        assertThat(asignacion.getEsAdminCurso()).isTrue();
    }

    @Test
    void cambiarEsAdminCursoDeDocenteNoAsignadoDevuelve404() {
        assertThatThrownBy(() -> service.actualizarDocente(40, 5, new ActualizarDocenteGrupoRequest(true)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
