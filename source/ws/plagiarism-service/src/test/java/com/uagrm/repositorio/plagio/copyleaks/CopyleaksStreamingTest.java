package com.uagrm.repositorio.plagio.copyleaks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * El envio a Copyleaks NO materializa el archivo: un "archivo" de 64 MB generado al vuelo (nunca existe como arreglo)
 * viaja por el cliente real hasta un servidor falso que solo calcula el SHA-256 de lo que recibe.
 * <ul>
 *   <li>El hash del cuerpo recibido es igual al de {@code {"base64":"<base64 del archivo>","filename":...}}
 *       calculado por otro camino (base64 por bloques de 3 KB).</li>
 *   <li>El servidor recibe el primer megabyte cuando del archivo se leyo apenas una parte: se envia mientras se lee
 *       (si el cliente cargara el archivo entero antes de enviar, ya lo habria leido completo).</li>
 * </ul>
 */
class CopyleaksStreamingTest {

    private static final long TAMANO = 64L * 1024 * 1024;
    private static final String API = "https://api.copyleaks.com";
    private static final String SCAN = "uagrm-0123456789abcdef0123-1";

    @Test
    void archivoGrandeViajaEnStreamingYLlegaIntacto() throws Exception {
        GeneradoAlVuelo archivo = new GeneradoAlVuelo(TAMANO);
        Sumidero sumidero = new Sumidero(archivo);
        List<String> llamadas = new ArrayList<>();
        ClientHttpRequestFactory servidorFalso = (URI uri, HttpMethod metodo) -> {
            llamadas.add(metodo + " " + uri);
            if (metodo == HttpMethod.POST) {
                MockClientHttpRequest login = new MockClientHttpRequest(metodo, uri);
                login.setResponse(json("{\"access_token\":\"tok\",\".expires\":\"" + Instant.now().plusSeconds(3600) + "\"}"));
                return login;
            }
            MockClientHttpRequest envio = new MockClientHttpRequest(metodo, uri) {
                @Override
                public OutputStream getBody() {
                    return sumidero;
                }
            };
            envio.setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.CREATED));
            return envio;
        };
        AtomicInteger aperturas = new AtomicInteger();
        CopyleaksCliente cliente = new CopyleaksCliente(MotorCopyleaksTest.props(""),
                RestClient.builder().requestFactory(servidorFalso));
        Map<String, Object> propiedades = Map.of("sandbox", true);

        cliente.enviarArchivo(SCAN, "tesis.pdf", () -> {
            aperturas.incrementAndGet();
            return archivo;
        }, propiedades);

        assertThat(llamadas).containsExactly("POST https://id.copyleaks.com/v3/account/login/api",
                "PUT " + API + "/v3/scans/submit/file/" + SCAN);
        assertThat(aperturas).hasValue(1);
        assertThat(archivo.leidos).isEqualTo(TAMANO);
        assertThat(sumidero.recibidos).isEqualTo(11 + 4 * ((TAMANO + 2) / 3)
                + "\",\"filename\":\"tesis.pdf\",\"properties\":{\"sandbox\":true}}".length());
        assertThat(sumidero.hash()).isEqualTo(hashEsperado("tesis.pdf", "{\"sandbox\":true}"));
        // al llegar el primer MB al servidor, del archivo de 64 MB se leyo solo un poco: no se cargo entero
        assertThat(sumidero.leidosAlPrimerMegabyte).isPositive().isLessThan(4L * 1024 * 1024);
    }

    @Test
    void escribirCuerpoNoCierraLaSalida() throws Exception {
        boolean[] cerrada = {false};
        java.io.ByteArrayOutputStream destino = new java.io.ByteArrayOutputStream() {
            @Override
            public void close() {
                cerrada[0] = true;
            }
        };
        CopyleaksCliente.escribirCuerpo(destino, new java.io.ByteArrayInputStream("hola".getBytes(StandardCharsets.UTF_8)),
                CopyleaksCliente.cola("a.pdf", Map.of()));
        assertThat(destino.toString(StandardCharsets.UTF_8))
                .isEqualTo("{\"base64\":\"aG9sYQ==\",\"filename\":\"a.pdf\",\"properties\":{}}");
        assertThat(cerrada[0]).isFalse();
    }

    /** SHA-256 del cuerpo esperado, codificando el archivo por bloques de 3 KB (multiplo de 3: mismo base64). */
    private static String hashEsperado(String nombre, String propiedades) throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update("{\"base64\":\"".getBytes(StandardCharsets.UTF_8));
        byte[] bloque = new byte[3 * 1024];
        for (long pos = 0; pos < TAMANO; pos += bloque.length) {
            int largo = (int) Math.min(bloque.length, TAMANO - pos);
            for (int i = 0; i < largo; i++) {
                bloque[i] = GeneradoAlVuelo.byteEn(pos + i);
            }
            byte[] trozo = largo == bloque.length ? bloque : java.util.Arrays.copyOf(bloque, largo);
            sha.update(Base64.getEncoder().encode(trozo));
        }
        sha.update(("\",\"filename\":\"" + nombre + "\",\"properties\":" + propiedades + "}").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sha.digest());
    }

    private static MockClientHttpResponse json(String cuerpo) {
        MockClientHttpResponse respuesta = new MockClientHttpResponse(cuerpo.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return respuesta;
    }

    /** Bytes deterministas generados al leer: el archivo nunca existe completo en memoria. */
    private static final class GeneradoAlVuelo extends InputStream {

        private final long tamano;
        private long leidos;

        GeneradoAlVuelo(long tamano) {
            this.tamano = tamano;
        }

        static byte byteEn(long pos) {
            return (byte) ((pos * 31 + (pos >>> 8)) & 0xff);
        }

        @Override
        public int read() {
            if (leidos >= tamano) {
                return -1;
            }
            return byteEn(leidos++) & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (leidos >= tamano) {
                return -1;
            }
            int n = (int) Math.min(len, tamano - leidos);
            for (int i = 0; i < n; i++) {
                b[off + i] = byteEn(leidos + i);
            }
            leidos += n;
            return n;
        }
    }

    /** Servidor falso: solo calcula el hash y cuenta; anota cuanto se habia leido del archivo al recibir 1 MB. */
    private static final class Sumidero extends OutputStream {

        private final GeneradoAlVuelo origen;
        private final MessageDigest sha;
        private long recibidos;
        private long leidosAlPrimerMegabyte = -1;

        Sumidero(GeneradoAlVuelo origen) throws NoSuchAlgorithmException {
            this.origen = origen;
            this.sha = MessageDigest.getInstance("SHA-256");
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            sha.update(b, off, len);
            recibidos += len;
            if (leidosAlPrimerMegabyte < 0 && recibidos >= 1024 * 1024) {
                leidosAlPrimerMegabyte = origen.leidos;
            }
        }

        String hash() {
            return HexFormat.of().formatHex(sha.digest());
        }

        @Override
        public void close() throws IOException {
            // nada que cerrar
        }
    }
}
