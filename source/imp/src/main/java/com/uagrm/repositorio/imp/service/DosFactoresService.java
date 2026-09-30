package com.uagrm.repositorio.imp.service;

import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.ActivarDosFactoresResponse;
import com.uagrm.repositorio.dto.auth.CodigoTotpRequest;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.TotpService;
import dev.samstevens.totp.qr.QrData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta y baja de 2FA desde el perfil del usuario. Activar es un proceso de dos pasos:
 * {@link #iniciarActivacion} genera el secreto y el QR; {@link #confirmarActivacion} lo habilita
 * solo si el usuario demuestra que lo escaneo, ingresando un codigo valido.
 */
@Service
@RequiredArgsConstructor
public class DosFactoresService {

    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final TotpService totpService;

    @Transactional
    public ActivarDosFactoresResponse iniciarActivacion(Jwt accessToken) {
        Usuario usuario = authService.buscarActivo(Integer.valueOf(accessToken.getSubject()));
        if (Boolean.TRUE.equals(usuario.getTwoFactorEnabled())) {
            throw NegocioException.conflicto("La verificacion en dos pasos ya esta activada.");
        }
        String secreto = totpService.generarSecreto();
        usuario.setTotpSecret(secreto);
        usuarioRepository.save(usuario);

        QrData qr = totpService.datosQr(secreto, usuario.getUsuario());
        return new ActivarDosFactoresResponse(secreto, qr.getUri(), totpService.qrDataUri(qr));
    }

    @Transactional
    public void confirmarActivacion(Jwt accessToken, CodigoTotpRequest request) {
        Usuario usuario = authService.buscarActivo(Integer.valueOf(accessToken.getSubject()));
        if (Boolean.TRUE.equals(usuario.getTwoFactorEnabled())) {
            throw NegocioException.conflicto("La verificacion en dos pasos ya esta activada.");
        }
        if (usuario.getTotpSecret() == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Activacion no iniciada",
                    "Primero solicite el codigo QR en /api/v1/auth/2fa/enable.");
        }
        validarCodigo(usuario, request.codigo());
        usuario.setTwoFactorEnabled(Boolean.TRUE);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void desactivar(Jwt accessToken, CodigoTotpRequest request) {
        Usuario usuario = authService.buscarActivo(Integer.valueOf(accessToken.getSubject()));
        if (!Boolean.TRUE.equals(usuario.getTwoFactorEnabled())) {
            throw NegocioException.conflicto("La verificacion en dos pasos no esta activada.");
        }
        validarCodigo(usuario, request.codigo());
        usuario.setTwoFactorEnabled(Boolean.FALSE);
        usuario.setTotpSecret(null);
        usuarioRepository.save(usuario);
    }

    private void validarCodigo(Usuario usuario, String codigo) {
        if (!totpService.esValido(usuario.getTotpSecret(), codigo)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Codigo incorrecto",
                    "El codigo de verificacion es incorrecto o expiro.");
        }
    }
}
