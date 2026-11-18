package com.uagrm.repositorio.imp.service.trabajo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.GrupoRepository;
import com.uagrm.repositorio.dao.repository.HashtagRepository;
import com.uagrm.repositorio.dao.repository.TipoTrabajoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.mapper.TrabajoMapperImpl;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.dto.trabajo.AprobarRequest;
import com.uagrm.repositorio.dto.trabajo.CalificarRequest;
import com.uagrm.repositorio.dto.trabajo.RechazarRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.documento.ArchivosTrabajo;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.ConfigFacultadMock;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reglas de negocio y permisos de TrabajoService con repositorios simulados: autor, coautor, docente del grupo,
 * docente ajeno, admin y otro estudiante; transiciones, notificaciones e historial.
 */
class TrabajoServiceTest {

    private static final int ID_DOCENTE_AJENO = 6;
    /** Docente del grupo 40 con es_admin_curso = true (TrabajoMock.ID_DOCENTE es docente del grupo sin ese rol). */
    private static final int ID_ADMIN_CURSO = 8;

    private final TrabajoRepository trabajoRepository = mock(TrabajoRepository.class);
    private final GrupoRepository grupoRepository = mock(GrupoRepository.class);
    private final TipoTrabajoRepository tipoTrabajoRepository = mock(TipoTrabajoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final HashtagRepository hashtagRepository = mock(HashtagRepository.class);
    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final TrabajoHistorialRepository historialRepository = mock(TrabajoHistorialRepository.class);
    private final GrupoDocenteRepository grupoDocenteRepository = mock(GrupoDocenteRepository.class);
    private final ArchivosTrabajo archivos = mock(ArchivosTrabajo.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final ConfigFacultadService configFacultad = mock(ConfigFacultadService.class);

    private TrabajoService service;
    private Trabajo trabajo;
    private Usuario coautor;

    @BeforeEach
    void setUp() {
        AccesoTrabajos acceso = new AccesoTrabajos(usuarioRepository, trabajoRepository, grupoDocenteRepository);
        service = new TrabajoService(trabajoRepository, grupoRepository, tipoTrabajoRepository, usuarioRepository,
                hashtagRepository, documentoRepository, historialRepository, grupoDocenteRepository, acceso, archivos,
                parametros, notificaciones, configFacultad, new TrabajoMapperImpl());

        coautor = TrabajoMock.coautor();
        usuario(TrabajoMock.autor());
        usuario(coautor);
        usuario(TrabajoMock.otroEstudiante());
        usuario(TrabajoMock.docente());
        usuario(TrabajoMock.admin());
        usuario(UsuarioMock.usuario(ID_DOCENTE_AJENO, "doc.ajeno", "DOCENTE"));
        usuario(UsuarioMock.usuario(ID_ADMIN_CURSO, "doc.admin", "DOCENTE"));
        when(grupoDocenteRepository.findEsAdminCurso(40, TrabajoMock.ID_DOCENTE)).thenReturn(Optional.of(false));
        when(grupoDocenteRepository.findEsAdminCurso(40, ID_ADMIN_CURSO)).thenReturn(Optional.of(true));
        when(grupoDocenteRepository.findEsAdminCurso(40, ID_DOCENTE_AJENO)).thenReturn(Optional.empty());
        when(configFacultad.configDe(1)).thenReturn(ConfigFacultadMock.con(1, 100, false));
        when(grupoDocenteRepository.findIdsDocentes(40)).thenReturn(List.of(TrabajoMock.ID_DOCENTE, ID_DOCENTE_AJENO));
        when(parametros.entero(Parametro.WORK_MAX_AUTORES)).thenReturn(5);
        when(documentoRepository.findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(anyInt())).thenReturn(List.of());
        when(historialRepository.findByTrabajoIdTrabajoOrderByFechaAscIdHistorialAsc(anyInt())).thenReturn(List.of());
        trabajoEn(EstadoTrabajo.BORRADOR);
    }

    // ---------- Visibilidad y acciones ----------

    @Test
    void autorVeSuBorradorConAccionesDeAutor() {
        TrabajoResponse respuesta = service.obtener(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO);
        assertThat(respuesta.acciones()).isEqualTo(new AccionesResponse(true, true, true, false, false, false, false));
        assertThat(respuesta.autores()).singleElement().satisfies(a -> assertThat(a.esUsuarioActual()).isTrue());
        assertThat(respuesta.periodo()).isEqualTo("2-2026");
        assertThat(respuesta.idFacultad()).isEqualTo(1);
    }

    @Test
    void coautorVeYPuedeEnviarPeroNoEsElUsuarioActualEnElAutorPrincipal() {
        trabajo.getAutores().add(coautor.getPersona());
        TrabajoResponse respuesta = service.obtener(jwt(TrabajoMock.ID_COAUTOR), TrabajoMock.ID_TRABAJO);
        assertThat(respuesta.acciones().enviar()).isTrue();
        assertThat(respuesta.autores()).extracting("idPersona")
                .containsExactly(TrabajoMock.ID_AUTOR, TrabajoMock.ID_COAUTOR);  // creador primero
        assertThat(respuesta.autores()).extracting("esUsuarioActual").containsExactly(false, true);
    }

    @Test
    void docenteDelGrupoVeConAccionesDeRevision() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        TrabajoResponse respuesta = service.obtener(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);
        assertThat(respuesta.acciones()).isEqualTo(new AccionesResponse(false, false, false, true, true, true, false));
    }

    @Test
    void adminVePeroSinAcciones() {
        trabajoEn(EstadoTrabajo.EN_REVISION);
        assertThat(service.obtener(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO).acciones())
                .isEqualTo(AccionesResponse.NINGUNA);
    }

    @Test
    void docenteAjenoYOtroEstudianteRecibenNoEncontrado() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        assertStatus(() -> service.obtener(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.obtener(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), TrabajoMock.ID_TRABAJO),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.tomarRevision(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.enviar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), TrabajoMock.ID_TRABAJO),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void adminNoPuedeRevisarNiElDocenteEditar() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        assertStatus(() -> service.aprobar(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO, new AprobarRequest(null)),
                HttpStatus.FORBIDDEN);
        trabajoEn(EstadoTrabajo.RECHAZADO);
        assertStatus(() -> service.actualizar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO, request(40, List.of())),
                HttpStatus.FORBIDDEN);
    }

    // ---------- Autores ----------

    @Test
    void enviarSinPdfDevuelve400() {
        assertStatus(() -> service.enviar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO), HttpStatus.BAD_REQUEST);
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.BORRADOR);
    }

    @Test
    void enviarPasaAPendienteYNotificaALosDocentesDelGrupo() {
        conPdf();
        trabajo.getAutores().add(coautor.getPersona());
        TrabajoResponse respuesta = service.enviar(jwt(TrabajoMock.ID_COAUTOR), TrabajoMock.ID_TRABAJO);

        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.PENDIENTE);
        assertThat(trabajo.getFechaEnvio()).isNotNull();
        assertThat(respuesta.acciones()).isEqualTo(AccionesResponse.NINGUNA);
        TrabajoHistorial historial = historialGuardado();
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoTrabajo.BORRADOR);
        assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoTrabajo.PENDIENTE);
        assertThat(historial.getUsuario().getIdUsuario()).isEqualTo(TrabajoMock.ID_COAUTOR);
        verify(notificaciones).notificar(eq(TrabajoMock.ID_DOCENTE), eq(TipoNotificacion.TRABAJO_ENVIADO), any(),
                any(), eq("/trabajos/7"));
        verify(notificaciones).notificar(eq(ID_DOCENTE_AJENO), eq(TipoNotificacion.TRABAJO_ENVIADO), any(), any(),
                eq("/trabajos/7"));
    }

    @Test
    void reenviarDesdeRechazadoYNoDesdePendiente() {
        conPdf();
        trabajoEn(EstadoTrabajo.RECHAZADO);
        service.enviar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO);
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.PENDIENTE);

        assertStatus(() -> service.enviar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO), HttpStatus.CONFLICT);
    }

    @Test
    void editarFueraDeEstadoEditableDevuelve409YCambiarGrupoSoloEnBorrador() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        assertStatus(() -> service.actualizar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, request(40, List.of())),
                HttpStatus.CONFLICT);
        trabajoEn(EstadoTrabajo.RECHAZADO);
        assertStatus(() -> service.actualizar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, request(41, List.of())),
                HttpStatus.CONFLICT);
    }

    @Test
    void eliminarSoloEnBorradorYBorraLosArchivosTrasElCommit() {
        trabajoEn(EstadoTrabajo.RECHAZADO);
        assertStatus(() -> service.eliminar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO), HttpStatus.CONFLICT);
        verify(trabajoRepository, never()).delete(any(Trabajo.class));

        trabajoEn(EstadoTrabajo.BORRADOR);
        List<String> rutas = List.of("trabajos/7/a.pdf");
        when(documentoRepository.findRutasByTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(rutas);
        service.eliminar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO);
        verify(trabajoRepository).delete(trabajo);
        verify(archivos).borrarTrasCommit(rutas);
    }

    // ---------- Crear ----------

    @Test
    void crearConCoautorYHashtagsEnMinusculas() {
        prepararCreacion();
        when(usuarioRepository.findByPersonaRegistroIn(any())).thenReturn(List.of(coautor));
        Hashtag spring = new Hashtag();
        spring.setTexto("springboot");
        when(hashtagRepository.findByTextoIn(any())).thenReturn(List.of(spring));

        TrabajoRequest request = new TrabajoRequest("Sistema de inventarios",
                "Analisis, diseno e implementacion de un sistema.", 40, 60, List.of("SpringBoot", "springboot"),
                List.of(TrabajoMock.REGISTRO_COAUTOR, TrabajoMock.REGISTRO_AUTOR));
        TrabajoResponse respuesta = service.crear(jwt(TrabajoMock.ID_AUTOR), request);

        assertThat(respuesta.estado()).isEqualTo(EstadoTrabajo.BORRADOR);
        assertThat(respuesta.autores()).extracting("idPersona")
                .containsExactly(TrabajoMock.ID_AUTOR, TrabajoMock.ID_COAUTOR);
        assertThat(respuesta.hashtags()).containsExactly("springboot");
        verify(hashtagRepository).crearSiNoExiste("springboot");
        assertThat(respuesta.idMateria()).isEqualTo(20);
        assertThat(respuesta.idSemestre()).isEqualTo(30);
        TrabajoHistorial historial = historialGuardado();
        assertThat(historial.getEstadoAnterior()).isNull();
        assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoTrabajo.BORRADOR);
    }

    @Test
    void soloUnEstudiantePuedeCrear() {
        assertStatus(() -> service.crear(jwt(TrabajoMock.ID_DOCENTE), request(40, List.of())), HttpStatus.FORBIDDEN);
    }

    @Test
    void grupoInactivoDevuelve409YTipoDeOtraFacultad400() {
        prepararCreacion();
        trabajo.getGrupo().setEstado(Estado.INACTIVO);
        assertStatus(() -> service.crear(jwt(TrabajoMock.ID_AUTOR), request(40, List.of())), HttpStatus.CONFLICT);

        trabajo.getGrupo().setEstado(Estado.ACTIVO);
        TipoTrabajo otraFacultad = CatalogoMock.tipoTrabajo(CatalogoMock.facultad(2, "FCET"));
        when(tipoTrabajoRepository.findById(60)).thenReturn(Optional.of(otraFacultad));
        assertThatThrownBy(() -> service.crear(jwt(TrabajoMock.ID_AUTOR), request(40, List.of())))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores()).containsKey("idTipoTrabajo"));
    }

    @Test
    void coautorQueNoEsEstudianteActivoDevuelve400() {
        prepararCreacion();
        Usuario docenteConRegistro = TrabajoMock.docente();
        docenteConRegistro.getPersona().setRegistro(219012346);
        when(usuarioRepository.findByPersonaRegistroIn(any())).thenReturn(List.of(docenteConRegistro));
        assertErrores(() -> service.crear(jwt(TrabajoMock.ID_AUTOR), request(40, List.of(219012346, 219000000))),
                "registrosCoautores");

        Usuario inactivo = TrabajoMock.coautor();
        inactivo.setEstado(Estado.INACTIVO);
        when(usuarioRepository.findByPersonaRegistroIn(any())).thenReturn(List.of(inactivo));
        assertErrores(() -> service.crear(jwt(TrabajoMock.ID_AUTOR),
                request(40, List.of(TrabajoMock.REGISTRO_COAUTOR))), "registrosCoautores");
    }

    @Test
    void masAutoresQueElMaximoDevuelve400() {
        prepararCreacion();
        when(parametros.entero(Parametro.WORK_MAX_AUTORES)).thenReturn(2);
        assertErrores(() -> service.crear(jwt(TrabajoMock.ID_AUTOR), request(40, List.of(1, 2))), "registrosCoautores");
        verifyNoInteractions(hashtagRepository);
    }

    // ---------- Revision ----------

    @Test
    void tomarRevisionNotificaALosAutores() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        when(usuarioRepository.findIdsByPersonas(any())).thenReturn(List.of(TrabajoMock.ID_AUTOR));
        service.tomarRevision(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.EN_REVISION);
        verify(notificaciones).notificar(eq(TrabajoMock.ID_AUTOR), eq(TipoNotificacion.TRABAJO_EN_REVISION), any(),
                any(), eq("/trabajos/7"));
    }

    @Test
    void aprobarConObservacionesYNoNotificarAlPropioActor() {
        trabajoEn(EstadoTrabajo.EN_REVISION);
        // Caso borde: la lista de destinatarios incluye al docente que aprueba; nunca se notifica a si mismo
        when(usuarioRepository.findIdsByPersonas(any())).thenReturn(List.of(TrabajoMock.ID_AUTOR, TrabajoMock.ID_DOCENTE));
        service.aprobar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO, new AprobarRequest("  Corregir la bibliografia. "));

        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.APROBADO_CON_OBSERVACIONES);
        assertThat(trabajo.getObservaciones()).isEqualTo("Corregir la bibliografia.");
        assertThat(historialGuardado().getComentario()).isEqualTo("Corregir la bibliografia.");
        verify(notificaciones).notificar(eq(TrabajoMock.ID_AUTOR),
                eq(TipoNotificacion.TRABAJO_APROBADO_CON_OBSERVACIONES), any(), any(), any());
        verify(notificaciones, never()).notificar(eq(TrabajoMock.ID_DOCENTE), any(), any(), any(), any());
    }

    @Test
    void aprobarSinObservacionesQuedaAprobadoYDesdeBorrador409() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        trabajo.setObservaciones("motivo de un rechazo anterior");
        when(usuarioRepository.findIdsByPersonas(any())).thenReturn(List.of(TrabajoMock.ID_AUTOR));
        service.aprobar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO, new AprobarRequest("   "));
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.APROBADO);
        assertThat(trabajo.getObservaciones()).isNull();
        verify(notificaciones).notificar(eq(TrabajoMock.ID_AUTOR), eq(TipoNotificacion.TRABAJO_APROBADO), any(), any(),
                any());

        trabajoEn(EstadoTrabajo.BORRADOR);
        assertStatus(() -> service.aprobar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO, new AprobarRequest(null)),
                HttpStatus.CONFLICT);
    }

    @Test
    void rechazarGuardaElMotivo() {
        trabajoEn(EstadoTrabajo.PENDIENTE);
        when(usuarioRepository.findIdsByPersonas(any())).thenReturn(List.of(TrabajoMock.ID_AUTOR));
        service.rechazar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO,
                new RechazarRequest("No corresponde al tema asignado."));
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.RECHAZADO);
        assertThat(trabajo.getObservaciones()).isEqualTo("No corresponde al tema asignado.");
        verify(notificaciones).notificar(eq(TrabajoMock.ID_AUTOR), eq(TipoNotificacion.TRABAJO_RECHAZADO), any(), any(),
                any());
    }

    // ---------- Calificar (CU18) ----------

    @Test
    void adminDeCursoCalificaPublicaGuardaHistorialYNotificaALosAutores() {
        trabajoEn(EstadoTrabajo.APROBADO);
        trabajo.getAutores().add(coautor.getPersona());
        when(usuarioRepository.findIdsByPersonas(any())).thenReturn(List.of(TrabajoMock.ID_AUTOR, TrabajoMock.ID_COAUTOR));

        TrabajoResponse respuesta = service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(85));

        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.EVALUADO);
        assertThat(trabajo.getCalificacion()).isEqualTo(85);
        assertThat(respuesta.calificacion()).isEqualTo(85);
        assertThat(respuesta.escalaCalifMax()).isEqualTo(100);
        assertThat(respuesta.acciones()).isEqualTo(AccionesResponse.NINGUNA);  // EVALUADO es final
        TrabajoHistorial historial = historialGuardado();
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoTrabajo.APROBADO);
        assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoTrabajo.EVALUADO);
        assertThat(historial.getComentario()).isEqualTo("Calificacion: 85/100");
        for (int idAutor : new int[]{TrabajoMock.ID_AUTOR, TrabajoMock.ID_COAUTOR}) {
            verify(notificaciones).notificar(eq(idAutor), eq(TipoNotificacion.TRABAJO_EVALUADO), any(),
                    org.mockito.ArgumentMatchers.argThat((String m) -> !m.contains("85")), eq("/trabajos/7"));
        }
    }

    @Test
    void calificacionEnLosLimitesDeLaEscalaDeLaFacultad() {
        when(configFacultad.configDe(1)).thenReturn(ConfigFacultadMock.con(1, 20, false));
        trabajoEn(EstadoTrabajo.APROBADO);
        service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(20));
        assertThat(trabajo.getCalificacion()).isEqualTo(20);
        trabajoEn(EstadoTrabajo.APROBADO);
        service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(0));
        assertThat(trabajo.getCalificacion()).isZero();
    }

    @Test
    void fueraDeRangoDevuelve400ConElRangoYNoCambiaNada() {
        when(configFacultad.configDe(1)).thenReturn(ConfigFacultadMock.con(1, 20, false));
        trabajoEn(EstadoTrabajo.APROBADO);
        assertThatThrownBy(() -> service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(21)))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> {
                    assertThat(((NegocioException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((NegocioException) ex).getErrores()).containsEntry("calificacion", "debe estar entre 0 y 20");
                });
        assertStatus(() -> service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(-1)),
                HttpStatus.BAD_REQUEST);
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.APROBADO);
        assertThat(trabajo.getCalificacion()).isNull();
        verifyNoInteractions(notificaciones);
    }

    @Test
    void docenteDelGrupoSinSerAdminDeCursoRecibe403YQuienNoLoVe404() {
        trabajoEn(EstadoTrabajo.APROBADO);
        assertStatus(() -> service.calificar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO, new CalificarRequest(80)),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.calificar(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO, new CalificarRequest(80)),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.calificar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, new CalificarRequest(80)),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.calificar(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO, new CalificarRequest(80)),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.calificar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), TrabajoMock.ID_TRABAJO,
                new CalificarRequest(80)), HttpStatus.NOT_FOUND);
        assertThat(trabajo.getEstado()).isEqualTo(EstadoTrabajo.APROBADO);
    }

    @Test
    void soloDesdeAprobadoDevuelve409EnOtroEstado() {
        for (EstadoTrabajo estado : EstadoTrabajo.values()) {
            if (estado == EstadoTrabajo.APROBADO) {
                continue;
            }
            trabajoEn(estado);
            assertStatus(() -> service.calificar(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO, new CalificarRequest(80)),
                    HttpStatus.CONFLICT);
        }
    }

    @Test
    void accionCalificarSoloParaElAdminDeCursoEnAprobadoYCalificacionSoloEnEvaluado() {
        trabajoEn(EstadoTrabajo.APROBADO);
        trabajo.setCalificacion(50);  // dato incoherente: fuera de EVALUADO nunca se muestra
        TrabajoResponse admin = service.obtener(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO);
        assertThat(admin.acciones()).isEqualTo(new AccionesResponse(false, false, false, false, false, false, true));
        assertThat(admin.calificacion()).isNull();
        assertThat(admin.escalaCalifMax()).isEqualTo(100);
        assertThat(service.obtener(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO).acciones().calificar()).isFalse();

        trabajoEn(EstadoTrabajo.EVALUADO);
        trabajo.setCalificacion(90);
        assertThat(service.obtener(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO).calificacion()).isEqualTo(90);
        assertThat(service.obtener(jwt(ID_ADMIN_CURSO), TrabajoMock.ID_TRABAJO).acciones().calificar()).isFalse();
    }

    @Test
    void bandejaConFiltroBorradorEstaVacia() {
        var pagina = service.listarRevision(jwt(TrabajoMock.ID_DOCENTE), EstadoTrabajo.BORRADOR, null, 0, 20);
        assertThat(pagina.contenido()).isEmpty();
        verify(trabajoRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
        assertStatus(() -> service.listarRevision(jwt(TrabajoMock.ID_AUTOR), null, null, 0, 20), HttpStatus.FORBIDDEN);
    }

    // ---------- helpers ----------

    private void usuario(Usuario usuario) {
        when(usuarioRepository.findConRolByIdUsuario(usuario.getIdUsuario())).thenReturn(Optional.of(usuario));
    }

    private void trabajoEn(EstadoTrabajo estado) {
        trabajo = TrabajoMock.trabajo(estado);
        when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(trabajoRepository.findParaActualizar(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
    }

    private void conPdf() {
        when(documentoRepository.existsByTrabajoIdTrabajoAndTipoAndExtension(TrabajoMock.ID_TRABAJO,
                TipoDocumento.ARCHIVO, "pdf")).thenReturn(true);
    }

    private void prepararCreacion() {
        when(grupoRepository.findById(40)).thenReturn(Optional.of(trabajo.getGrupo()));
        when(tipoTrabajoRepository.findById(60)).thenReturn(Optional.of(trabajo.getTipoTrabajo()));
        when(trabajoRepository.save(any())).thenAnswer(inv -> {
            Trabajo nuevo = inv.getArgument(0);
            nuevo.setIdTrabajo(TrabajoMock.ID_TRABAJO);
            return nuevo;
        });
    }

    private TrabajoHistorial historialGuardado() {
        ArgumentCaptor<TrabajoHistorial> captor = ArgumentCaptor.forClass(TrabajoHistorial.class);
        verify(historialRepository).save(captor.capture());
        return captor.getValue();
    }

    private static TrabajoRequest request(int idGrupo, List<Integer> coautores) {
        return new TrabajoRequest("Sistema de inventarios", "Analisis, diseno e implementacion de un sistema.",
                idGrupo, 60, List.of(), coautores);
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }

    private static void assertStatus(Runnable accion, HttpStatus status) {
        assertThatThrownBy(accion::run).isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(status);
    }

    private static void assertErrores(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOf(NegocioException.class)
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores()).containsKey(campo));
    }
}
