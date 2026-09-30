package com.uagrm.repositorio.imp.service;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.CambioPasswordRequest;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.LogoutRequest;
import com.uagrm.repositorio.dto.auth.RefreshRequest;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.auth.VerificarDosFactoresRequest;
import com.uagrm.repositorio.dto.mapper.UsuarioMapper;
import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.DosFactoresPendienteService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.TotpService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Mismo mensaje para usuario inexistente y contrasena incorrecta: no revela cual de los dos fallo. */
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contrasena incorrectos.";

    /** Password de relleno: con un usuario inexistente igual se ejecuta BCrypt, asi ambos casos tardan lo mismo. */
    private static final String PASSWORD_FICTICIA = "usuario-inexistente";

    private final UsuarioRepository usuarioRepository;
    private final ConfigFacultadRepository configFacultadRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final RevocacionTokenService revocacionTokenService;
    private final DosFactoresPendienteService dosFactoresPendienteService;
    private final UsuarioMapper usuarioMapper;
    private final SeguridadProperties props;

    private volatile String hashFicticio;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<Usuario> encontrado = usuarioRepository.findByUsuario(request.usuario());
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.password(), hashFicticio());
            throw NegocioException.noAutorizado(CREDENCIALES_INVALIDAS);
        }
        Usuario usuario = encontrado.get();
        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw NegocioException.noAutorizado(CREDENCIALES_INVALIDAS);
        }
        validarActivo(usuario);

        if (Boolean.TRUE.equals(usuario.getTwoFactorEnabled())) {
            return LoginResponse.pendienteDosFactores(dosFactoresPendienteService.crear(usuario.getIdUsuario()));
        }
        return LoginResponse.completo(emitirTokens(usuario));
    }

    @Transactional(readOnly = true)
    public TokenResponse verificarDosFactores(VerificarDosFactoresRequest request) {
        Integer idUsuario = dosFactoresPendienteService.registrarIntento(request.tokenTemporal());
        Usuario usuario = buscarActivo(idUsuario);
        if (!totpService.esValido(usuario.getTotpSecret(), request.codigo())) {
            throw NegocioException.noAutorizado("El codigo de verificacion es incorrecto.");
        }
        dosFactoresPendienteService.eliminar(request.tokenTemporal());
        return emitirTokens(usuario);
    }

    /** Rotacion: cada refresh token se usa una sola vez; el usado queda en la blacklist. */
    @Transactional(readOnly = true)
    public TokenResponse refrescar(RefreshRequest request) {
        Jwt refresh = jwtService.decodificarRefresh(request.refreshToken());
        if (revocacionTokenService.estaRevocado(refresh)) {
            throw NegocioException.noAutorizado("La sesion fue cerrada. Inicie sesion nuevamente.");
        }
        Usuario usuario = buscarActivo(Integer.valueOf(refresh.getSubject()));
        revocacionTokenService.revocar(refresh);
        return emitirTokens(usuario);
    }

    public void logout(Jwt accessToken, LogoutRequest request) {
        revocacionTokenService.revocar(accessToken);
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            Jwt refresh = jwtService.decodificarRefresh(request.refreshToken());
            if (refresh.getSubject().equals(accessToken.getSubject())) {
                revocacionTokenService.revocar(refresh);
            }
        }
    }

    /**
     * Cambia la contrasena, revoca todas las sesiones previas del usuario (en cualquier dispositivo)
     * y devuelve tokens nuevos para que pueda continuar sin volver a iniciar sesion.
     */
    @Transactional
    public TokenResponse cambiarPassword(Jwt accessToken, CambioPasswordRequest request) {
        Usuario usuario = buscarActivo(Integer.valueOf(accessToken.getSubject()));
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPassword())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos", "La contrasena actual es incorrecta.");
        }
        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPassword())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos",
                    "La nueva contrasena debe ser distinta de la actual.");
        }
        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuario.setRequiereCambioPassword(Boolean.FALSE);
        usuarioRepository.save(usuario);

        revocacionTokenService.revocarTodosDesde(usuario.getIdUsuario(), Instant.now());
        return emitirTokens(usuario);
    }

    Usuario buscarActivo(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> NegocioException.noAutorizado("La cuenta ya no existe."));
        validarActivo(usuario);
        return usuario;
    }

    private static void validarActivo(Usuario usuario) {
        if (usuario.getEstado() != Estado.ACTIVO) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Cuenta desactivada",
                    "Su cuenta esta desactivada. Contacte al administrador de su facultad.");
        }
    }

    private String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = passwordEncoder.encode(PASSWORD_FICTICIA);
        }
        return hashFicticio;
    }

    private TokenResponse emitirTokens(Usuario usuario) {
        JwtService.ParTokens par = jwtService.generar(usuario, accessTtl(usuario));
        return new TokenResponse(par.accessToken(), par.refreshToken(), "Bearer",
                par.accessTtl().toSeconds(), usuarioMapper.toSesion(usuario));
    }

    /** Duracion del access token: ConfigFacultad.jwtExpiracionHoras de la facultad del usuario, o el valor por defecto. */
    private Duration accessTtl(Usuario usuario) {
        Carrera carrera = usuario.getPersona().getCarrera();
        if (carrera == null) {
            return props.jwt().accessTtlPorDefecto();
        }
        return configFacultadRepository.findByFacultadIdFacultad(carrera.getFacultad().getIdFacultad())
                .map(config -> Duration.ofHours(config.getJwtExpiracionHoras()))
                .orElse(props.jwt().accessTtlPorDefecto());
    }
}
