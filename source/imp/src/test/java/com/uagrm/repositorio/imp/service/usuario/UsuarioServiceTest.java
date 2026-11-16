package com.uagrm.repositorio.imp.service.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.repository.TipoUsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.mapper.UsuarioMapperImpl;
import com.uagrm.repositorio.dto.usuario.ActualizarUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.CrearUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.RestablecerPasswordRequest;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class UsuarioServiceTest {

    private static final int ID_ADMIN = 1;
    private static final int ID_DOCENTE = 5;

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PersonaRepository personaRepository = mock(PersonaRepository.class);
    private final TipoUsuarioRepository tipoUsuarioRepository = mock(TipoUsuarioRepository.class);
    private final CarreraRepository carreraRepository = mock(CarreraRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RevocacionTokenService revocacionTokenService = mock(RevocacionTokenService.class);

    private UsuarioService service;
    private Usuario admin;
    private Usuario docente;
    private Carrera inf;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, personaRepository, tipoUsuarioRepository, carreraRepository,
                passwordEncoder, revocacionTokenService, new UsuarioMapperImpl());
        admin = UsuarioMock.usuario(ID_ADMIN, "admin", TipoUsuario.ADMIN);
        docente = UsuarioMock.usuario(ID_DOCENTE, "doc.maria", TipoUsuario.DOCENTE);
        inf = CatalogoMock.carrera(CatalogoMock.facultad());
        when(usuarioRepository.findDetalleByIdUsuario(ID_ADMIN)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findDetalleByIdUsuario(ID_DOCENTE)).thenReturn(Optional.of(docente));
        for (String rol : new String[]{TipoUsuario.ADMIN, TipoUsuario.DOCENTE, TipoUsuario.ESTUDIANTE}) {
            TipoUsuario tipo = new TipoUsuario();
            tipo.setNombre(rol);
            when(tipoUsuarioRepository.findByNombre(rol)).thenReturn(Optional.of(tipo));
        }
        when(carreraRepository.findById(inf.getIdCarrera())).thenReturn(Optional.of(inf));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- crear ----------

    @Test
    void crearGuardaElLoginEnMinusculasConPasswordCifradaYCambioObligatorio() {
        UsuarioResponse creado = service.crear(crear("Doc.Ana", TipoUsuario.DOCENTE, null, null));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario usuario = captor.getValue();
        assertThat(usuario.getUsuario()).isEqualTo("doc.ana");
        assertThat(usuario.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(usuario.getRequiereCambioPassword()).isTrue();
        assertThat(usuario.getPersona().getCorreo()).isEqualTo("ana.rios@uagrm.edu.bo");
        assertThat(creado.rol()).isEqualTo(TipoUsuario.DOCENTE);
        assertThat(creado.siglaCarrera()).isNull();
        verify(passwordEncoder).encode("Inicial2026");
    }

    @Test
    void estudianteSinRegistroOCarreraDevuelve400() {
        assertThatThrownBy(() -> service.crear(crear("est.ana", TipoUsuario.ESTUDIANTE, null, inf.getIdCarrera())))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.crear(crear("est.ana", TipoUsuario.ESTUDIANTE, 219000111, null)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void estudianteConCarreraDevuelveSuSigla() {
        UsuarioResponse creado = service.crear(crear("est.ana", TipoUsuario.ESTUDIANTE, 219000111, inf.getIdCarrera()));

        assertThat(creado.registro()).isEqualTo(219000111);
        assertThat(creado.siglaCarrera()).isEqualTo("INF");
    }

    @Test
    void duplicadosDevuelven409() {
        when(usuarioRepository.existsByUsuario("doc.ana")).thenReturn(true);
        esperar(HttpStatus.CONFLICT, () -> service.crear(crear("doc.ana", TipoUsuario.DOCENTE, null, null)));

        when(usuarioRepository.existsByUsuario("doc.ana")).thenReturn(false);
        when(personaRepository.existsByCiAndIdPersonaNot("4443332", 0)).thenReturn(true);
        esperar(HttpStatus.CONFLICT, () -> service.crear(crear("doc.ana", TipoUsuario.DOCENTE, null, null)));

        when(personaRepository.existsByCiAndIdPersonaNot("4443332", 0)).thenReturn(false);
        when(personaRepository.existsByCorreoIgnoreCaseAndIdPersonaNot("ana.rios@uagrm.edu.bo", 0)).thenReturn(true);
        esperar(HttpStatus.CONFLICT, () -> service.crear(crear("doc.ana", TipoUsuario.DOCENTE, null, null)));

        when(personaRepository.existsByCorreoIgnoreCaseAndIdPersonaNot("ana.rios@uagrm.edu.bo", 0)).thenReturn(false);
        when(personaRepository.existsByRegistroAndIdPersonaNot(219000111, 0)).thenReturn(true);
        esperar(HttpStatus.CONFLICT,
                () -> service.crear(crear("est.ana", TipoUsuario.ESTUDIANTE, 219000111, inf.getIdCarrera())));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void carreraInexistenteDevuelve404() {
        esperar(HttpStatus.NOT_FOUND, () -> service.crear(crear("est.ana", TipoUsuario.ESTUDIANTE, 219000111, 99)));
    }

    // ---------- actualizar / desactivar ----------

    @Test
    void cambiarElRolCierraLasSesiones() {
        UsuarioResponse actualizado = service.actualizar(jwt(ID_ADMIN), ID_DOCENTE,
                actualizar(TipoUsuario.ADMIN, null));

        assertThat(actualizado.rol()).isEqualTo(TipoUsuario.ADMIN);
        assertThat(actualizado.usuario()).isEqualTo("doc.maria");
        verify(revocacionTokenService).revocarTodosDesde(eq(ID_DOCENTE), any());
    }

    @Test
    void desactivarPorPutCierraLasSesionesYEditarDatosNo() {
        service.actualizar(jwt(ID_ADMIN), ID_DOCENTE, actualizar(TipoUsuario.DOCENTE, null));
        verify(revocacionTokenService, never()).revocarTodosDesde(anyInt(), any());

        UsuarioResponse desactivado = service.actualizar(jwt(ID_ADMIN), ID_DOCENTE,
                actualizar(TipoUsuario.DOCENTE, Estado.INACTIVO));
        assertThat(desactivado.estado()).isEqualTo(Estado.INACTIVO);
        verify(revocacionTokenService).revocarTodosDesde(eq(ID_DOCENTE), any());
    }

    @Test
    void elAdminNoPuedeQuitarseElRolNiDesactivarse() {
        esperar(HttpStatus.BAD_REQUEST,
                () -> service.actualizar(jwt(ID_ADMIN), ID_ADMIN, actualizar(TipoUsuario.DOCENTE, null)));
        esperar(HttpStatus.BAD_REQUEST,
                () -> service.actualizar(jwt(ID_ADMIN), ID_ADMIN, actualizar(TipoUsuario.ADMIN, Estado.INACTIVO)));
        esperar(HttpStatus.BAD_REQUEST, () -> service.desactivar(jwt(ID_ADMIN), ID_ADMIN));
        assertThat(admin.getEstado()).isEqualTo(Estado.ACTIVO);
        verify(revocacionTokenService, never()).revocarTodosDesde(anyInt(), any());
    }

    @Test
    void desactivarEsBorradoLogicoYCierraSesiones() {
        UsuarioResponse desactivado = service.desactivar(jwt(ID_ADMIN), ID_DOCENTE);

        assertThat(desactivado.estado()).isEqualTo(Estado.INACTIVO);
        verify(revocacionTokenService).revocarTodosDesde(eq(ID_DOCENTE), any());
        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    void usuarioInexistenteDevuelve404() {
        esperar(HttpStatus.NOT_FOUND, () -> service.obtener(99));
        esperar(HttpStatus.NOT_FOUND, () -> service.desactivar(jwt(ID_ADMIN), 99));
    }

    @Test
    void restablecerPasswordObligaACambiarlaYCierraSesiones() {
        service.restablecerPassword(ID_DOCENTE, new RestablecerPasswordRequest("Temporal2026"));

        assertThat(docente.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(docente.getRequiereCambioPassword()).isTrue();
        verify(passwordEncoder).encode("Temporal2026");
        verify(revocacionTokenService).revocarTodosDesde(eq(ID_DOCENTE), any());
    }

    // ---------- CI como texto ----------

    @Test
    void ciConComplementoSeGuardaSinEspaciosYEnMayusculas() {
        CrearUsuarioRequest request = new CrearUsuarioRequest("doc.ana", "Inicial2026", TipoUsuario.DOCENTE, "Ana",
                "Rios", "4443332-1a", "ana.rios@uagrm.edu.bo", "+59170012345", null, null, null);

        UsuarioResponse creado = service.crear(request);

        assertThat(creado.ci()).isEqualTo("4443332-1A");
        assertThat(creado.celular()).isEqualTo("+59170012345");
        verify(personaRepository).existsByCiAndIdPersonaNot("4443332-1A", 0);
    }

    // ---------- un estudiante siempre tiene carrera y registro ----------

    @Test
    void registroEIdCarreraOmitidosConservanLosActuales() {
        Usuario estudiante = estudiante();

        UsuarioResponse actualizado = service.actualizar(jwt(ID_ADMIN), ID_ESTUDIANTE,
                new ActualizarUsuarioRequest(TipoUsuario.ESTUDIANTE, "Juan", "Perez", "7654321",
                        "est.juan@uagrm.edu.bo", null, null, null, null, null));

        assertThat(actualizado.registro()).isEqualTo(219000111);
        assertThat(actualizado.idCarrera()).isEqualTo(inf.getIdCarrera());
        assertThat(estudiante.getPersona().getRegistro()).isEqualTo(219000111);
        assertThat(estudiante.getPersona().getCarrera()).isSameAs(inf);
    }

    @Test
    void registroEIdCarreraEnviadosSeCambian() {
        estudiante();
        Carrera sis = CatalogoMock.carrera(CatalogoMock.facultad());
        sis.setIdCarrera(11);
        sis.setSigla("SIS");
        when(carreraRepository.findById(11)).thenReturn(Optional.of(sis));

        UsuarioResponse actualizado = service.actualizar(jwt(ID_ADMIN), ID_ESTUDIANTE,
                new ActualizarUsuarioRequest(TipoUsuario.ESTUDIANTE, "Juan", "Perez", "7654321",
                        "est.juan@uagrm.edu.bo", null, null, 219000222, 11, null));

        assertThat(actualizado.registro()).isEqualTo(219000222);
        assertThat(actualizado.siglaCarrera()).isEqualTo("SIS");
    }

    @Test
    void pasarAEstudianteSinCarreraNiRegistroDevuelve400() {
        esperar(HttpStatus.BAD_REQUEST, () -> service.actualizar(jwt(ID_ADMIN), ID_DOCENTE,
                actualizar(TipoUsuario.ESTUDIANTE, null)));
        esperar(HttpStatus.BAD_REQUEST, () -> service.actualizar(jwt(ID_ADMIN), ID_DOCENTE,
                new ActualizarUsuarioRequest(TipoUsuario.ESTUDIANTE, "Maria", "Lopez Suarez", "7654321",
                        "doc.maria@uagrm.edu.bo", null, null, 219000333, null, null)));
        assertThat(docente.getTipoUsuario().getNombre()).isEqualTo(TipoUsuario.DOCENTE);
        assertThat(docente.getPersona().getRegistro()).isNull();
    }

    @Test
    void pasarAEstudianteConCarreraYRegistro() {
        UsuarioResponse actualizado = service.actualizar(jwt(ID_ADMIN), ID_DOCENTE,
                new ActualizarUsuarioRequest(TipoUsuario.ESTUDIANTE, "Maria", "Lopez Suarez", "7654321",
                        "doc.maria@uagrm.edu.bo", null, null, 219000333, inf.getIdCarrera(), null));

        assertThat(actualizado.rol()).isEqualTo(TipoUsuario.ESTUDIANTE);
        assertThat(actualizado.registro()).isEqualTo(219000333);
        assertThat(actualizado.siglaCarrera()).isEqualTo("INF");
    }

    // ---------- helpers ----------

    private static final int ID_ESTUDIANTE = 7;

    private Usuario estudiante() {
        Usuario estudiante = UsuarioMock.usuario(ID_ESTUDIANTE, "est.juan", TipoUsuario.ESTUDIANTE);
        estudiante.getPersona().setRegistro(219000111);
        estudiante.getPersona().setCarrera(inf);
        when(usuarioRepository.findDetalleByIdUsuario(ID_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        return estudiante;
    }

    private static CrearUsuarioRequest crear(String usuario, String rol, Integer registro, Integer idCarrera) {
        return new CrearUsuarioRequest(usuario, "Inicial2026", rol, " Ana ", "Rios", "4443332",
                "Ana.Rios@UAGRM.edu.bo", null, null, registro, idCarrera);
    }

    private static ActualizarUsuarioRequest actualizar(String rol, Integer estado) {
        return new ActualizarUsuarioRequest(rol, "Maria", "Lopez Suarez", "7654321", "doc.maria@uagrm.edu.bo",
                null, null, null, null, estado);
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }

    private static void esperar(HttpStatus status, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(NegocioException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }
}
