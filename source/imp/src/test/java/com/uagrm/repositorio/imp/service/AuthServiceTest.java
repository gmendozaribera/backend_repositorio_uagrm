package com.uagrm.repositorio.imp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.CambioPasswordRequest;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.RefreshRequest;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.mapper.UsuarioMapperImpl;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.DosFactoresPendienteService;
import com.uagrm.repositorio.imp.redis.LimiteIntentosService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.TotpService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.ConfigFacultadMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthServiceTest {

    private static final String PASSWORD = "Secreta2026";
    private static final String IP = "203.0.113.7";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ConfigFacultadService configFacultad = mock(ConfigFacultadService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final TotpService totpService = mock(TotpService.class);
    private final RevocacionTokenService revocacionTokenService = mock(RevocacionTokenService.class);
    private final DosFactoresPendienteService dosFactoresPendienteService = mock(DosFactoresPendienteService.class);
    private final LimiteIntentosService limiteIntentos = mock(LimiteIntentosService.class);

    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, configFacultad, passwordEncoder, jwtService,
                totpService, revocacionTokenService, dosFactoresPendienteService, limiteIntentos, new UsuarioMapperImpl());
        when(configFacultad.configDe(any())).thenReturn(ConfigFacultadMock.porDefecto(null));

        usuario = UsuarioMock.estudiante();
        when(usuarioRepository.findByUsuario(usuario.getUsuario())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(usuario.getIdUsuario())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, UsuarioMock.PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generar(eq(usuario), any()))
                .thenReturn(new JwtService.ParTokens("access", "refresh", Duration.ofHours(1)));
    }

    @Test
    void loginCorrectoSinDosFactoresDevuelveTokensYRol() {
        LoginResponse respuesta = authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP);

        assertThat(respuesta.requiereDosFactores()).isFalse();
        assertThat(respuesta.tokens().accessToken()).isEqualTo("access");
        assertThat(respuesta.tokens().expiraEn()).isEqualTo(3600);
        assertThat(respuesta.tokens().usuario().rol()).isEqualTo("ESTUDIANTE");
    }

    @Test
    void passwordIncorrectaYUsuarioInexistenteDanElMismoError() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$ficticio");

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), "otra"), IP))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Usuario o contrasena incorrectos.");
        assertThatThrownBy(() -> authService.login(new LoginRequest("no.existe", PASSWORD), IP))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Usuario o contrasena incorrectos.");

        // Con usuario inexistente igual se ejecuta BCrypt (mismo tiempo de respuesta)
        verify(passwordEncoder).matches(PASSWORD, "$2a$10$ficticio");
    }

    @Test
    void conDosFactoresNoEmiteTokensHastaVerificarElCodigo() {
        usuario.setTwoFactorEnabled(true);
        when(dosFactoresPendienteService.crear(usuario.getIdUsuario())).thenReturn("token-temporal");

        LoginResponse respuesta = authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP);

        assertThat(respuesta.requiereDosFactores()).isTrue();
        assertThat(respuesta.tokenTemporal()).isEqualTo("token-temporal");
        assertThat(respuesta.tokens()).isNull();
        verify(jwtService, never()).generar(any(), any());
    }

    @Test
    void cuentaDesactivadaEsRechazadaConForbidden() {
        usuario.setEstado(Estado.INACTIVO);

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void cambiarPasswordQuitaElFlagYRevocaTodasLasSesionesPrevias() {
        usuario.setRequiereCambioPassword(true);
        when(passwordEncoder.matches("Nueva2026x", UsuarioMock.PASSWORD_HASH)).thenReturn(false);
        when(passwordEncoder.encode("Nueva2026x")).thenReturn("$2a$10$nuevo");

        TokenResponse tokens = authService.cambiarPassword(jwtDe(usuario), new CambioPasswordRequest(PASSWORD, "Nueva2026x"));

        assertThat(usuario.getRequiereCambioPassword()).isFalse();
        assertThat(usuario.getPassword()).isEqualTo("$2a$10$nuevo");
        assertThat(tokens.accessToken()).isEqualTo("access");
        verify(revocacionTokenService).revocarTodosDesde(eq(usuario.getIdUsuario()), any());
    }

    @Test
    void refreshRevocadoEsRechazado() {
        Jwt refresh = jwtDe(usuario);
        when(jwtService.decodificarRefresh("r")).thenReturn(refresh);
        when(revocacionTokenService.estaRevocado(refresh)).thenReturn(true);

        assertThatThrownBy(() -> authService.refrescar(new RefreshRequest("r")))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(jwtService, never()).generar(any(), any());
    }

    // ---------- duracion del access token ----------

    @Test
    void elAccessTokenDuraLasHorasConfiguradasParaLaFacultadDelUsuario() {
        usuario.getPersona().setCarrera(CatalogoMock.carrera(CatalogoMock.facultad()));
        Integer idFacultad = usuario.getPersona().getCarrera().getFacultad().getIdFacultad();
        when(configFacultad.configDe(idFacultad)).thenReturn(ConfigFacultadMock.personalizada(idFacultad, 8));

        authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP);

        verify(jwtService).generar(usuario, Duration.ofHours(8));
    }

    @Test
    void sinCarreraUsaElValorPorDefectoDeDominio() {
        usuario.getPersona().setCarrera(null);
        when(configFacultad.configDe(null)).thenReturn(ConfigFacultadMock.porDefecto(null));

        authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP);

        verify(configFacultad).configDe(null);
        verify(jwtService).generar(usuario, Duration.ofHours(ConfigFacultadMock.HORAS_POR_DEFECTO));
    }

    // ---------- limite de intentos por IP ----------

    @Test
    void credencialesIncorrectasSumanUnIntentoFallidoDeLaIp() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$ficticio");

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), "otra"), IP))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> authService.login(new LoginRequest("no.existe", PASSWORD), IP))
                .isInstanceOf(NegocioException.class);

        verify(limiteIntentos, times(2)).verificar(LimiteIntentosService.Operacion.LOGIN, IP);
        verify(limiteIntentos, times(2)).registrarFallo(LimiteIntentosService.Operacion.LOGIN, IP);
    }

    @Test
    void loginExitosoYCuentaDesactivadaNoCuentanComoFallo() {
        authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP);
        usuario.setEstado(Estado.INACTIVO);
        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP))
                .isInstanceOf(NegocioException.class);

        verify(limiteIntentos, never()).registrarFallo(any(), any());
    }

    @Test
    void ipBloqueadaRecibe429SinVerificarLaContrasena() {
        doThrow(new LimiteIntentosException(900)).when(limiteIntentos)
                .verificar(LimiteIntentosService.Operacion.LOGIN, IP);

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD), IP))
                .isInstanceOfSatisfying(LimiteIntentosException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getRetryAfterSegundos()).isEqualTo(900);
                    assertThat(ex.getMessage()).isEqualTo("Demasiados intentos fallidos. Intente de nuevo en 15 minutos.");
                });
        verify(passwordEncoder, never()).matches(any(), any());
        verify(limiteIntentos, never()).registrarFallo(any(), any());
    }

    private static Jwt jwtDe(Usuario usuario) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(usuario.getIdUsuario().toString())
                .jti("jti-1")
                .build();
    }
}
