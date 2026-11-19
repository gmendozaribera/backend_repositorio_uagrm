package com.uagrm.repositorio.imp.security;

import com.uagrm.repositorio.imp.config.SeguridadProperties;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import org.springframework.stereotype.Service;

/**
 * TOTP RFC 6238 compatible con Google Authenticator: SHA1, 6 digitos, periodo de 30 s.
 *
 * <p>El secreto se guarda CIFRADO en la BD ({@link CifradorTotp}, {@code v1:...}): {@link #cifrar} antes de guardar
 * y {@link #esValido} lo descifra solo en memoria para verificar el codigo. Un secreto en claro (anterior al
 * cifrado) lo convierte auth-service al arrancar; si quedara alguno, la verificacion falla (fail-closed).</p>
 */
@Service
public class TotpService {

    private static final int DIGITOS = 6;
    private static final int PERIODO_SEGUNDOS = 30;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier;
    private final SeguridadProperties props;
    private final CifradorTotp cifrador;

    public TotpService(SeguridadProperties props, CifradorTotp cifrador) {
        this.props = props;
        this.cifrador = cifrador;
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setTimePeriod(PERIODO_SEGUNDOS);
        verifier.setAllowedTimePeriodDiscrepancy(1); // tolera ±30 s de desfase de reloj del celular
        this.codeVerifier = verifier;
    }

    public String generarSecreto() {
        return secretGenerator.generate();
    }

    public QrData datosQr(String secreto, String cuenta) {
        return new QrData.Builder()
                .label(cuenta)
                .secret(secreto)
                .issuer(props.dosFactores().emisor())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITOS)
                .period(PERIODO_SEGUNDOS)
                .build();
    }

    public String qrDataUri(QrData datos) {
        try {
            return Utils.getDataUriForImage(qrGenerator.generate(datos), qrGenerator.getImageMimeType());
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("No se pudo generar el codigo QR", ex);
        }
    }

    /** Secreto en claro (recien generado) → formato guardado {@code v1:...}. */
    public String cifrar(String secreto) {
        return cifrador.cifrar(secreto);
    }

    /**
     * @param secretoGuardado el valor de la BD ({@code v1:...}); null = el usuario no tiene 2FA
     * @throws IllegalStateException si no se puede descifrar (llave incorrecta, dato alterado o en claro): es un
     *                               problema del servidor (500), nunca se acepta el codigo
     */
    public boolean esValido(String secretoGuardado, String codigo) {
        if (secretoGuardado == null || secretoGuardado.isEmpty()) {
            return false;
        }
        return codeVerifier.isValidCode(cifrador.descifrar(secretoGuardado), codigo);
    }
}
