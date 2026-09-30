package com.uagrm.repositorio.imp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.CambioPasswordRequest;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.RefreshRequest;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.mapper.UsuarioMapperImpl;
import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.DosFactoresPendienteService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.TotpService;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthServiceTest {

    private static final String PASSWORD = "Secreta2026";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ConfigFacultadRepository configFacultadRepository = mock(ConfigFacultadRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final TotpService totpService = mock(TotpService.class);
    private final RevocacionTokenService revocacionTokenService = mock(RevocacionTokenService.class);
    private final DosFactoresPendienteService dosFactoresPendienteService = mock(DosFactoresPendienteService.class);

    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        SeguridadProperties props = new SeguridadProperties(
                new SeguridadProperties.Jwt("c2VjcmV0bw==", "test", Duration.ofHours(1), Duration.ofDays(7)),
                new SeguridadProperties.DosFactores("Test", Duration.ofMinutes(5), 5),
                List.of("http://localhost:4200"));
        authService = new AuthService(usuarioRepository, configFacultadRepository, passwordEncoder, jwtService,
                totpService, revocacionTokenService, dosFactoresPendienteService, new UsuarioMapperImpl(), props);

        usuario = UsuarioMock.estudiante();
        when(usuarioRepository.findByUsuario(usuario.getUsuario())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(usuario.getIdUsuario())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, UsuarioMock.PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generar(eq(usuario), any()))
                .thenReturn(new JwtService.ParTokens("access", "refresh", Duration.ofHours(1)));
    }

    @Test
    void loginCorrectoSinDosFactoresDevuelveTokensYRol() {
        LoginResponse respuesta = authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD));

        assertThat(respuesta.requiereDosFactores()).isFalse();
        assertThat(respuesta.tokens().accessToken()).isEqualTo("access");
        assertThat(respuesta.tokens().expiraEn()).isEqualTo(3600);
        assertThat(respuesta.tokens().usuario().rol()).isEqualTo("ESTUDIANTE");
    }

    @Test
    void passwordIncorrectaYUsuarioInexistenteDanElMismoError() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$ficticio");

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), "otra")))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Usuario o contrasena incorrectos.");
        assertThatThrownBy(() -> authService.login(new LoginRequest("no.existe", PASSWORD)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Usuario o contrasena incorrectos.");

        // Con usuario inexistente igual se ejecuta BCrypt (mismo tiempo de respuesta)
        verify(passwordEncoder).matches(PASSWORD, "$2a$10$ficticio");
    }

    @Test
    void conDosFactoresNoEmiteTokensHastaVerificarElCodigo() {
        usuario.setTwoFactorEnabled(true);
        when(dosFactoresPendienteService.crear(usuario.getIdUsuario())).thenReturn("token-temporal");

        LoginResponse respuesta = authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD));

        assertThat(respuesta.requiereDosFactores()).isTrue();
        assertThat(respuesta.tokenTemporal()).isEqualTo("token-temporal");
        assertThat(respuesta.tokens()).isNull();
        verify(jwtService, never()).generar(any(), any());
    }

    @Test
    void cuentaDesactivadaEsRechazadaConForbidden() {
        usuario.setEstado(Estado.INACTIVO);

        assertThatThrownBy(() -> authService.login(new LoginRequest(usuario.getUsuario(), PASSWORD)))
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

    private static Jwt jwtDe(Usuario usuario) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(usuario.getIdUsuario().toString())
                .jti("jti-1")
                .build();
    }
}
