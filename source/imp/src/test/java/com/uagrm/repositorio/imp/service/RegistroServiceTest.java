package com.uagrm.repositorio.imp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.EstudianteHabilitadoRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.repository.TipoUsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.RegistroRequest;
import com.uagrm.repositorio.dto.auth.RegistroResponse;
import com.uagrm.repositorio.dto.mapper.UsuarioMapperImpl;
import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.DosFactoresPendienteService;
import com.uagrm.repositorio.imp.redis.LimiteIntentosService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.TotpService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.PadronMock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistroServiceTest {

    private static final String PASSWORD_DEFAULT = "Uagrm2026";
    private static final String IP = "203.0.113.7";
    /** BCrypt con costo bajo: el test verifica el flujo, no la fuerza del hash. */
    private static final PasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);
    private static final String HASH = BCRYPT.encode(PASSWORD_DEFAULT);

    private final EstudianteHabilitadoRepository padronRepository = mock(EstudianteHabilitadoRepository.class);
    private final PersonaRepository personaRepository = mock(PersonaRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final TipoUsuarioRepository tipoUsuarioRepository = mock(TipoUsuarioRepository.class);
    private final LimiteIntentosService limiteIntentos = mock(LimiteIntentosService.class);

    private RegistroService registroService;
    private EstudianteHabilitado padron;
    private final RegistroRequest request = new RegistroRequest(PadronMock.CI, PadronMock.REGISTRO);

    @BeforeEach
    void setUp() {
        registroService = new RegistroService(padronRepository, personaRepository, usuarioRepository,
                tipoUsuarioRepository, limiteIntentos);
        padron = PadronMock.estudianteHabilitado(CatalogoMock.carrera(CatalogoMock.facultad()), HASH);
        when(padronRepository.findByCiAndRegistro(PadronMock.CI, PadronMock.REGISTRO)).thenReturn(Optional.of(padron));
        TipoUsuario estudiante = new TipoUsuario();
        estudiante.setIdTipoUsuario(3);
        estudiante.setNombre(TipoUsuario.ESTUDIANTE);
        when(tipoUsuarioRepository.findByNombre(TipoUsuario.ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        when(usuarioRepository.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setIdUsuario(99);
            return u;
        });
    }

    @Test
    void creaPersonaYCuentaConLaPasswordDelPadronSinVolverACifrarla() {
        RegistroResponse respuesta = registroService.registrar(request, IP);

        assertThat(respuesta.usuario()).isEqualTo("219012345");
        Usuario usuario = usuarioGuardado();
        assertThat(usuario.getUsuario()).isEqualTo("219012345");
        assertThat(usuario.getPassword()).isEqualTo(HASH);
        assertThat(usuario.getRequiereCambioPassword()).isTrue();
        assertThat(usuario.getTipoUsuario().getNombre()).isEqualTo(TipoUsuario.ESTUDIANTE);
        assertThat(usuario.getEstado()).isEqualTo(1);
        assertThat(usuario.getPersona().getNombre()).isEqualTo("Juan Carlos");
        assertThat(usuario.getPersona().getRegistro()).isEqualTo(PadronMock.REGISTRO);
        assertThat(usuario.getPersona().getCi()).isEqualTo(PadronMock.CI);
        assertThat(usuario.getPersona().getCarrera().getSigla()).isEqualTo("INF");
        verify(personaRepository).save(usuario.getPersona());
    }

    @Test
    void despuesDeRegistrarsePuedeIniciarSesionConLaPasswordDefaultYDebeCambiarla() {
        registroService.registrar(request, IP);
        Usuario usuario = usuarioGuardado();

        // AuthService real con BCrypt real: el login usa exactamente el hash copiado del padron
        UsuarioRepository login = mock(UsuarioRepository.class);
        when(login.findByUsuario("219012345")).thenReturn(Optional.of(usuario));
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generar(any(), any())).thenReturn(new JwtService.ParTokens("a", "r", Duration.ofHours(1)));
        ConfigFacultadRepository config = mock(ConfigFacultadRepository.class);
        when(config.findByFacultadIdFacultad(anyInt())).thenReturn(Optional.empty());
        AuthService authService = new AuthService(login, config, BCRYPT, jwtService, mock(TotpService.class),
                mock(RevocacionTokenService.class), mock(DosFactoresPendienteService.class),
                mock(LimiteIntentosService.class), new UsuarioMapperImpl(),
                new SeguridadProperties(
                        new SeguridadProperties.Jwt("c2VjcmV0bw==", "test", Duration.ofHours(1), Duration.ofDays(7)),
                        new SeguridadProperties.DosFactores("Test", Duration.ofMinutes(5), 5),
                        List.of("http://localhost:4200")));

        LoginResponse respuesta = authService.login(new LoginRequest("219012345", PASSWORD_DEFAULT), IP);

        assertThat(respuesta.tokens()).isNotNull();
        assertThat(respuesta.tokens().usuario().requiereCambioPassword()).isTrue();
        assertThat(respuesta.tokens().usuario().rol()).isEqualTo(TipoUsuario.ESTUDIANTE);
    }

    @Test
    void ciYRegistroQueNoEstanEnElPadronDevuelve404() {
        when(padronRepository.findByCiAndRegistro(PadronMock.CI, PadronMock.REGISTRO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registroService.registrar(request, IP))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo(
                            "No encontrado en el padron institucional. Contacte a su facultad.");
                });
    }

    @Test
    void estudianteInhabilitadoDevuelveElMismo404() {
        padron.setHabilitado(false);

        assertThatThrownBy(() -> registroService.registrar(request, IP))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo(RegistroService.NO_EN_PADRON);
                });
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void personaConEseCiDevuelve409CuentaExistente() {
        when(personaRepository.existsByCi(PadronMock.CI)).thenReturn(true);
        esperarConflicto(RegistroService.CUENTA_EXISTENTE);
    }

    @Test
    void personaConEseRegistroDevuelve409() {
        when(personaRepository.existsByRegistro(PadronMock.REGISTRO)).thenReturn(true);
        esperarConflicto(RegistroService.CUENTA_EXISTENTE);
    }

    @Test
    void loginYaUsadoDevuelve409() {
        when(usuarioRepository.existsByUsuario("219012345")).thenReturn(true);
        esperarConflicto(RegistroService.CUENTA_EXISTENTE);
    }

    @Test
    void correoDeOtraPersonaDevuelve409() {
        when(personaRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(true);
        esperarConflicto("Su correo ya pertenece a otra cuenta. Contacte a su facultad.");
    }

    private void esperarConflicto(String detalle) {
        assertThatThrownBy(() -> registroService.registrar(request, IP))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(detalle);
                });
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- CI como texto y limite de intentos ----------

    @Test
    void ciConComplementoSeBuscaSinEspaciosYEnMayusculas() {
        padron.setCi("1234567-1A");
        when(padronRepository.findByCiAndRegistro("1234567-1A", PadronMock.REGISTRO)).thenReturn(Optional.of(padron));

        registroService.registrar(new RegistroRequest(" 1234567-1a ", PadronMock.REGISTRO), IP);

        assertThat(usuarioGuardado().getPersona().getCi()).isEqualTo("1234567-1A");
    }

    @Test
    void noEncontradoYCuentaExistenteSumanUnIntentoFallido() {
        when(padronRepository.findByCiAndRegistro(PadronMock.CI, PadronMock.REGISTRO)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> registroService.registrar(request, IP)).isInstanceOf(NegocioException.class);

        when(padronRepository.findByCiAndRegistro(PadronMock.CI, PadronMock.REGISTRO)).thenReturn(Optional.of(padron));
        when(personaRepository.existsByCi(PadronMock.CI)).thenReturn(true);
        assertThatThrownBy(() -> registroService.registrar(request, IP)).isInstanceOf(NegocioException.class);

        verify(limiteIntentos, times(2)).registrarFallo(LimiteIntentosService.Operacion.REGISTRO, IP);
    }

    @Test
    void registroExitosoNoCuentaComoFallo() {
        registroService.registrar(request, IP);

        verify(limiteIntentos).verificar(LimiteIntentosService.Operacion.REGISTRO, IP);
        verify(limiteIntentos, never()).registrarFallo(any(), any());
    }

    @Test
    void ipBloqueadaRecibe429SinConsultarElPadron() {
        doThrow(new LimiteIntentosException(60)).when(limiteIntentos)
                .verificar(LimiteIntentosService.Operacion.REGISTRO, IP);

        assertThatThrownBy(() -> registroService.registrar(request, IP))
                .isInstanceOfSatisfying(LimiteIntentosException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(padronRepository, never()).findByCiAndRegistro(any(), any());
    }

    private Usuario usuarioGuardado() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }
}
