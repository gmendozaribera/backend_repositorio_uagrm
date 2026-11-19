package com.uagrm.repositorio.imp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uagrm.repositorio.imp.dominio.CifradorDominio;
import com.uagrm.repositorio.imp.dominio.CifradorDominioTest;
import org.junit.jupiter.api.Test;

/** Cifrado del secreto TOTP: misma base AES-GCM que el de dominio, pero con su propia llave y mensajes. */
public class CifradorTotpTest {

    /** "dev-solo-cifrado-totp-auth-32byt" (la de application-dev.yml de auth-service). */
    public static final String LLAVE = "ZGV2LXNvbG8tY2lmcmFkby10b3RwLWF1dGgtMzJieXQ=";
    private static final String SECRETO = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private final CifradorTotp cifrador = new CifradorTotp(LLAVE);

    @Test
    void idaYVueltaConFormatoV1QueCabeEnLaColumna() {
        String cifrado = cifrador.cifrar(SECRETO);

        assertThat(cifrado).startsWith("v1:").doesNotContain(SECRETO);
        assertThat(CifradorAesGcm.estaCifrado(cifrado)).isTrue();
        assertThat(CifradorAesGcm.estaCifrado(SECRETO)).isFalse();
        // V13: VARCHAR(128). Un secreto Base32 de 32 caracteres ocupa 83
        assertThat(cifrado).hasSizeLessThanOrEqualTo(128);
        assertThat(cifrador.descifrar(cifrado)).isEqualTo(SECRETO);
    }

    @Test
    void conOtraLlaveFallaSinRevelarElSecretoYNombraSuVariable() {
        String cifrado = cifrador.cifrar(SECRETO);

        assertThatThrownBy(() -> new CifradorTotp(CifradorDominioTest.LLAVE).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_CIFRADO_KEY")
                .hasMessageNotContaining(SECRETO)
                .hasNoCause();
        // La llave de dominio no abre un secreto TOTP (llaves separadas)
        assertThatThrownBy(() -> new CifradorDominio(CifradorDominioTest.LLAVE).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unSecretoEnClaroNoSeAceptaComoCifrado() {
        assertThatThrownBy(() -> cifrador.descifrar(SECRETO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secreto TOTP no esta cifrado")
                .hasMessageNotContaining(SECRETO);
    }

    @Test
    void sinLlaveNoEstaDisponible() {
        CifradorTotp sinLlave = new CifradorTotp("");

        assertThat(sinLlave.disponible()).isFalse();
        assertThatThrownBy(() -> sinLlave.cifrar(SECRETO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.seguridad.totp-cifrado-key (TOTP_CIFRADO_KEY)");
    }
}
