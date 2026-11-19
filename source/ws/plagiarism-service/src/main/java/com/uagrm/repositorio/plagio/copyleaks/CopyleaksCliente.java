package com.uagrm.repositorio.plagio.copyleaks;

import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP de la API v3 de Copyleaks (verificada con docs.copyleaks.com en 2026-11):
 * <ul>
 *   <li>Login: {@code POST https://id.copyleaks.com/v3/account/login/api} con {@code {email, key}} →
 *       {@code {access_token, .issued, .expires}}. El token dura 48 h.</li>
 *   <li>Envio: {@code PUT https://api.copyleaks.com/v3/scans/submit/file/{scanId}} con
 *       {@code {base64, filename, properties}} y {@code Authorization: Bearer}; responde 201 sin cuerpo. El cuerpo
 *       se envia en streaming (base64 codificado al vuelo desde MinIO).</li>
 * </ul>
 *
 * <p>El token se guarda SOLO en memoria (no en Redis: no se confirmo una key para el) y se renueva 5 minutos antes
 * de vencer o si Copyleaks responde 401. Con varias replicas cada una hace su propio login (dos logins cada 48 h
 * no son un problema). Nunca se loguean la API key, el token ni el contenido de los archivos.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.plagio.motor", havingValue = "COPYLEAKS")
public class CopyleaksCliente {

    static final Duration MARGEN_RENOVACION = Duration.ofMinutes(5);
    static final Duration VIDA_POR_DEFECTO = Duration.ofHours(47);
    private static final byte[] INICIO_CUERPO = "{\"base64\":\"".getBytes(StandardCharsets.UTF_8);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final PlagioProperties.Copyleaks config;
    private Token token;

    private record Token(String valor, Instant expira) {
    }

    @Autowired
    public CopyleaksCliente(PlagioProperties props) {
        this(props, RestClient.builder().requestFactory(tiempos()));
    }

    /** Para tests: permite enlazar un servidor falso (MockRestServiceServer) al builder. */
    CopyleaksCliente(PlagioProperties props, RestClient.Builder builder) {
        this.http = builder.build();
        this.config = props.copyleaks();
    }

    /**
     * Envia un archivo a analizar, en streaming: el cuerpo {@code {"base64":"...","filename":...,"properties":{...}}}
     * se escribe mientras se lee el archivo (ver {@link #escribirCuerpo}), sin tener nunca el archivo ni su base64
     * completos en memoria.
     *
     * @param contenido   abre el archivo (MinIO). Se llama una vez por intento: si Copyleaks responde 401 el cuerpo
     *                    ya se consumio y el reintento vuelve a abrirlo
     * @param propiedades objeto {@code properties} de Copyleaks (sandbox, webhooks, scanning, indexing...)
     * @throws org.springframework.web.client.RestClientException si Copyleaks no responde o rechaza el envio
     */
    public void enviarArchivo(String scanId, String nombreArchivo, Supplier<InputStream> contenido,
                              Map<String, Object> propiedades) {
        byte[] cola = cola(nombreArchivo, propiedades);
        try {
            put(scanId, contenido, cola, tokenVigente());
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.info("Copyleaks rechazo el token (401): se renueva y se reintenta una vez");
            invalidar();
            put(scanId, contenido, cola, tokenVigente());
        }
    }

    private void put(String scanId, Supplier<InputStream> contenido, byte[] cola, String bearer) {
        // El archivo se abre ANTES de conectar con Copyleaks: si MinIO falla no se llega a abrir la conexion
        try (InputStream archivo = contenido.get()) {
            http.put()
                    .uri(config.apiUrl() + "/v3/scans/submit/file/{scanId}", scanId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(bearer))
                    .body(salida -> escribirCuerpo(salida, archivo, cola))
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException ex) {
            // solo el cierre del stream de MinIO llega aqui (los errores de red los envuelve RestClient)
            throw new UncheckedIOException("No se pudo cerrar el archivo de " + scanId, ex);
        }
    }

    /**
     * Escribe el JSON del envio: {@code {"base64":"} + el archivo codificado al vuelo + {@code ","filename":...}}.
     * El codificador base64 envuelve la salida sin cerrarla (cerrarlo solo agrega el relleno final "=") y despues se
     * escribe el resto del JSON. La memoria usada es la de un buffer, sin importar el tamano del archivo.
     */
    static void escribirCuerpo(OutputStream salida, InputStream archivo, byte[] cola) throws IOException {
        salida.write(INICIO_CUERPO);
        try (OutputStream base64 = Base64.getEncoder().wrap(new SinCerrar(salida))) {
            archivo.transferTo(base64);
        }
        salida.write(cola);
        salida.flush();
    }

    /** {@code ","filename":"...","properties":{...}}} con escape JSON correcto (Jackson). */
    static byte[] cola(String nombreArchivo, Map<String, Object> propiedades) {
        ByteArrayOutputStream cola = new ByteArrayOutputStream();
        cola.writeBytes("\",\"filename\":".getBytes(StandardCharsets.UTF_8));
        cola.writeBytes(JSON.writeValueAsBytes(nombreArchivo));
        cola.writeBytes(",\"properties\":".getBytes(StandardCharsets.UTF_8));
        cola.writeBytes(JSON.writeValueAsBytes(propiedades));
        cola.write('}');
        return cola.toByteArray();
    }

    /** Pasa todo a la salida real pero no la cierra (la cierra la fabrica HTTP al terminar el request). */
    private static final class SinCerrar extends OutputStream {

        private final OutputStream destino;

        SinCerrar(OutputStream destino) {
            this.destino = destino;
        }

        @Override
        public void write(int b) throws IOException {
            destino.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            destino.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            destino.flush();
        }

        @Override
        public void close() throws IOException {
            destino.flush();
        }
    }

    /** Token en memoria; login si no hay o le quedan menos de 5 minutos. */
    synchronized String tokenVigente() {
        if (token == null || Instant.now().isAfter(token.expira().minus(MARGEN_RENOVACION))) {
            token = login();
        }
        return token.valor();
    }

    synchronized void invalidar() {
        token = null;
    }

    private Token login() {
        JsonNode respuesta = http.post()
                .uri(config.loginUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("email", config.email(), "key", config.apiKey()))
                .retrieve()
                .body(JsonNode.class);
        if (respuesta == null || !respuesta.hasNonNull("access_token")) {
            throw new IllegalStateException("Copyleaks no devolvio access_token en el login");
        }
        Instant expira = expiracion(respuesta.path(".expires").asString(""));
        log.info("Login en Copyleaks correcto; token valido hasta {}", expira);
        return new Token(respuesta.get("access_token").asString(), expira);
    }

    /** ".expires" en ISO-8601; si falta o no se entiende, 47 h (el token dura 48). */
    static Instant expiracion(String texto) {
        if (texto != null && !texto.isBlank()) {
            try {
                return OffsetDateTime.parse(texto).toInstant();
            } catch (DateTimeParseException ex) {
                try {
                    return Instant.parse(texto);
                } catch (DateTimeParseException otra) {
                    log.warn("Fecha .expires de Copyleaks no reconocida; se asumen 47 h");
                }
            }
        }
        return Instant.now().plus(VIDA_POR_DEFECTO);
    }

    /**
     * Fabrica SIN buffer: SimpleClientHttpRequestFactory (Spring 6.1+) escribe el cuerpo directo en la conexion; sin
     * Content-Length usa Transfer-Encoding: chunked (bloques de 4 KB). Ojo: agregar interceptores al RestClient lo
     * haria bufferizar el cuerpo entero en memoria.
     */
    private static SimpleClientHttpRequestFactory tiempos() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));  // un archivo grande en base64 tarda en subir
        return factory;
    }
}
