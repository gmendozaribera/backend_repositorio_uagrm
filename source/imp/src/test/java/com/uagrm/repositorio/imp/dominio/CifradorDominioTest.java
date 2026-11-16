package com.uagrm.repositorio.imp.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

public class CifradorDominioTest {

    /** Llave de prueba: Base64 de 32 bytes. */
    public static final String LLAVE = Base64.getEncoder().encodeToString("llave-de-prueba-de-32-bytes-ok!!".getBytes());
    private static final String OTRA_LLAVE = Base64.getEncoder().encodeToString("otra-llave-de-prueba-32-bytes-!!".getBytes());

    private final CifradorDominio cifrador = new CifradorDominio(LLAVE);

    @Test
    void idaYVuelta() {
        String cifrado = cifrador.cifrar("Contrasena SMTP con ñ y espacios ");

        assertThat(cifrado).startsWith(CifradorDominio.PREFIJO).doesNotContain("Contrasena");
        assertThat(cifrador.descifrar(cifrado)).isEqualTo("Contrasena SMTP con ñ y espacios ");
    }

    @Test
    void cadaCifradoUsaUnIvDistinto() {
        String primero = cifrador.cifrar("misma-clave");
        String segundo = cifrador.cifrar("misma-clave");

        assertThat(primero).isNotEqualTo(segundo);
        byte[] ivPrimero = Base64.getDecoder().decode(primero.substring(CifradorDominio.PREFIJO.length()));
        byte[] ivSegundo = Base64.getDecoder().decode(segundo.substring(CifradorDominio.PREFIJO.length()));
        assertThat(java.util.Arrays.copyOf(ivPrimero, 12)).isNotEqualTo(java.util.Arrays.copyOf(ivSegundo, 12));
        assertThat(cifrador.descifrar(primero)).isEqualTo(cifrador.descifrar(segundo));
    }

    @Test
    void conLlaveIncorrectaFallaSinRevelarElValor() {
        String cifrado = cifrador.cifrar("clave-secreta");

        assertThatThrownBy(() -> new CifradorDominio(OTRA_LLAVE).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("clave-secreta")
                .hasNoCause();
    }

    @Test
    void unDatoAlteradoNoSeDescifra() {
        String cifrado = cifrador.cifrar("clave-secreta");
        byte[] bytes = Base64.getDecoder().decode(cifrado.substring(CifradorDominio.PREFIJO.length()));
        bytes[15] ^= 1;  // un bit del texto cifrado
        String alterado = CifradorDominio.PREFIJO + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cifrador.descifrar(alterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("texto-plano")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void vacioSeGuardaVacio() {
        assertThat(cifrador.cifrar("")).isEmpty();
        assertThat(cifrador.descifrar("")).isEmpty();
    }

    @Test
    void sinLlaveNoEstaDisponibleYNoCifra() {
        CifradorDominio sinLlave = new CifradorDominio("");

        assertThat(sinLlave.disponible()).isFalse();
        assertThat(cifrador.disponible()).isTrue();
        assertThatThrownBy(() -> sinLlave.cifrar("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void llaveDeLargoIncorrectoFallaAlArrancar() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CifradorDominio(corta)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new CifradorDominio("no es base64!")).isInstanceOf(IllegalStateException.class);
    }
}
