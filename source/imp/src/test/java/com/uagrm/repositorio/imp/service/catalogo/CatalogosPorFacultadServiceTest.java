package com.uagrm.repositorio.imp.service.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Fase;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.repository.FaseRepository;
import com.uagrm.repositorio.dao.repository.MateriaRepository;
import com.uagrm.repositorio.dao.repository.SemestreRepository;
import com.uagrm.repositorio.dao.repository.TipoTrabajoRepository;
import com.uagrm.repositorio.dto.catalogo.CarreraRequest;
import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.dto.catalogo.FaseRequest;
import com.uagrm.repositorio.dto.catalogo.MateriaRequest;
import com.uagrm.repositorio.dto.catalogo.SemestreRequest;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoRequest;
import com.uagrm.repositorio.dto.mapper.CatalogoMapper;
import com.uagrm.repositorio.dto.mapper.CatalogoMapperImpl;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import com.uagrm.repositorio.mockup.CatalogoMock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/**
 * Carrera, Materia, Semestre, Fase y TipoTrabajo comparten la misma estructura (catalogo de una facultad):
 * referencia a facultad activa, unicidad, borrado logico e invalidacion de su cache.
 */
class CatalogosPorFacultadServiceTest {

    private final FacultadRepository facultadRepository = mock(FacultadRepository.class);
    private final MateriaRepository materiaRepository = mock(MateriaRepository.class);
    private final SemestreRepository semestreRepository = mock(SemestreRepository.class);
    private final CarreraRepository carreraRepository = mock(CarreraRepository.class);
    private final FaseRepository faseRepository = mock(FaseRepository.class);
    private final TipoTrabajoRepository tipoTrabajoRepository = mock(TipoTrabajoRepository.class);
    private final CatalogoCacheService cache = mock(CatalogoCacheService.class);
    private final CatalogoMapper mapper = new CatalogoMapperImpl();

    private CarreraService carreraService;
    private MateriaService materiaService;
    private SemestreService semestreService;
    private FaseService faseService;
    private TipoTrabajoService tipoTrabajoService;
    private Facultad ficct;

    @BeforeEach
    void setUp() {
        ReferenciasCatalogo referencias = new ReferenciasCatalogo(facultadRepository, materiaRepository, semestreRepository);
        carreraService = new CarreraService(carreraRepository, referencias, mapper, cache);
        materiaService = new MateriaService(materiaRepository, referencias, mapper, cache);
        semestreService = new SemestreService(semestreRepository, referencias, mapper, cache);
        faseService = new FaseService(faseRepository, referencias, mapper, cache);
        tipoTrabajoService = new TipoTrabajoService(tipoTrabajoRepository, referencias, mapper, cache);

        ficct = CatalogoMock.facultad();
        when(facultadRepository.findById(1)).thenReturn(Optional.of(ficct));
        when(carreraRepository.save(any(Carrera.class))).thenAnswer(inv -> inv.getArgument(0));
        when(materiaRepository.save(any(Materia.class))).thenAnswer(inv -> inv.getArgument(0));
        when(semestreRepository.save(any(Semestre.class))).thenAnswer(inv -> inv.getArgument(0));
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tipoTrabajoRepository.save(any(TipoTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Carrera ----------

    @Test
    void carreraListadoFiltradoPorFacultadUsaLaCacheDeEsaFacultad() {
        when(cache.listar(eq(EntidadCatalogo.CARRERAS), eq(1), eq(CarreraResponse.class), any()))
                .thenReturn(List.of(CatalogoMock.carreraResponse()));

        assertThat(carreraService.listar(1, false)).hasSize(1);
        verify(carreraRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void carreraEnFacultadInexistenteDevuelve404() {
        when(facultadRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carreraService.crear(new CarreraRequest("Sistemas", "SIS", 99, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void carreraEnFacultadInactivaDevuelve409() {
        ficct.setEstado(Estado.INACTIVO);

        assertThatThrownBy(() -> carreraService.crear(new CarreraRequest("Sistemas", "SIS", 1, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void editarCarreraDeFacultadYaInactivaSinCambiarlaEsPermitido() {
        Carrera carrera = CatalogoMock.carrera(ficct);
        ficct.setEstado(Estado.INACTIVO);
        when(carreraRepository.findById(10)).thenReturn(Optional.of(carrera));

        CarreraResponse editada = carreraService.actualizar(10, new CarreraRequest("Informatica", "inf", 1, null));

        assertThat(editada.nombre()).isEqualTo("Informatica");
        assertThat(editada.idFacultad()).isEqualTo(1);
        verify(cache).invalidar(EntidadCatalogo.CARRERAS);
    }

    @Test
    void carreraDesactivarInvalidaSuCache() {
        when(carreraRepository.findById(10)).thenReturn(Optional.of(CatalogoMock.carrera(ficct)));

        assertThat(carreraService.desactivar(10).estado()).isEqualTo(Estado.INACTIVO);
        verify(cache).invalidar(EntidadCatalogo.CARRERAS);
    }

    // ---------- Materia ----------

    @Test
    void materiaConSiglaRepetidaEnLaFacultadDevuelve409() {
        when(materiaRepository.existsByFacultadIdFacultadAndSiglaAndIdMateriaNot(1, "INF412", 0)).thenReturn(true);

        assertThatThrownBy(() -> materiaService.crear(new MateriaRequest("Software", "inf412", 1, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(materiaRepository, never()).save(any());
    }

    @Test
    void materiaCreadaQuedaActivaEnSuFacultad() {
        var creada = materiaService.crear(new MateriaRequest("Base de Datos I", "inf312", 1, null));

        assertThat(creada.sigla()).isEqualTo("INF312");
        assertThat(creada.idFacultad()).isEqualTo(1);
        assertThat(creada.estado()).isEqualTo(Estado.ACTIVO);
        verify(cache).invalidar(EntidadCatalogo.MATERIAS);
    }

    // ---------- Semestre ----------

    @Test
    void semestreRepetidoEnLaFacultadDevuelve409() {
        when(semestreRepository.existsByFacultadIdFacultadAndAnioAndSemestreAndIdSemestreNot(1, 2026, 2, 0))
                .thenReturn(true);

        assertThatThrownBy(() -> semestreService.crear(new SemestreRequest(2, 2026, 1, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void semestreCreadoInvalidaSuCache() {
        var creado = semestreService.crear(new SemestreRequest(1, 2027, 1, null));

        assertThat(creado.semestre()).isEqualTo(1);
        assertThat(creado.anio()).isEqualTo(2027);
        verify(cache).invalidar(EntidadCatalogo.SEMESTRES);
    }

    // ---------- Fase ----------

    @Test
    void faseActualizadaSinDescripcionLaBorra() {
        Fase fase = CatalogoMock.fase(ficct);
        when(faseRepository.findById(50)).thenReturn(Optional.of(fase));

        var editada = faseService.actualizar(50, new FaseRequest("Perfil", null, 1, null));

        assertThat(editada.descripcion()).isNull();
        verify(cache).invalidar(EntidadCatalogo.FASES);
    }

    @Test
    void faseConNombreRepetidoDevuelve409() {
        when(faseRepository.existsByFacultadIdFacultadAndNombreIgnoreCaseAndIdFaseNot(1, "Perfil", 0)).thenReturn(true);

        assertThatThrownBy(() -> faseService.crear(new FaseRequest(" Perfil ", null, 1, null)))
                .isInstanceOf(NegocioException.class);
    }

    // ---------- TipoTrabajo ----------

    @Test
    void tipoTrabajoPuedeCrearseInactivo() {
        var creado = tipoTrabajoService.crear(new TipoTrabajoRequest("Tesis", 1, Estado.INACTIVO));

        assertThat(creado.estado()).isEqualTo(Estado.INACTIVO);
        verify(cache).invalidar(EntidadCatalogo.TIPOS_TRABAJO);
    }

    @Test
    void tipoTrabajoConInactivosConsultaPostgresDirecto() {
        when(tipoTrabajoRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(CatalogoMock.tipoTrabajo(ficct)));

        assertThat(tipoTrabajoService.listar(1, true)).hasSize(1);
        verify(cache, never()).listar(any(), any(), any(), any());
    }
}
