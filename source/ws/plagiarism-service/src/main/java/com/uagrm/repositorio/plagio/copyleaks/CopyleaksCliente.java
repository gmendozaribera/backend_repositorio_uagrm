package com.uagrm.repositorio.plagio.copyleaks;

import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Cliente HTTP de la API v3 de Copyleaks (verificada con docs.copyleaks.com en 2026-11):
 * <ul>
 *   <li>Login: {@code POST https://id.copyleaks.com/v3/account/login/api} con {@code {email, key}} →
 *       {@code {access_token, .issued, .expires}}. El token dura 48 h.</li>
 *   <li>Envio: {@code PUT https://api.copyleaks.com/v3/scans/submit/file/{scanId}} con
 *       {@code {base64, filename, properties}} y {@code Authorization: Bearer}; responde 201 sin cuerpo.</li>
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
     * Envia un archivo a analizar.
     *
     * @param propiedades objeto {@code properties} de Copyleaks (sandbox, webhooks, scanning, indexing...)
     * @throws org.springframework.web.client.RestClientException si Copyleaks no responde o rechaza el envio
     */
    public void enviarArchivo(String scanId, String nombreArchivo, String base64, Map<String, Object> propiedades) {
        Map<String, Object> cuerpo = Map.of("base64", base64, "filename", nombreArchivo, "properties", propiedades);
        try {
            put(scanId, cuerpo, tokenVigente());
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.info("Copyleaks rechazo el token (401): se renueva y se reintenta una vez");
            invalidar();
            put(scanId, cuerpo, tokenVigente());
        }
    }

    private void put(String scanId, Map<String, Object> cuerpo, String bearer) {
        http.put()
                .uri(config.apiUrl() + "/v3/scans/submit/file/{scanId}", scanId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(bearer))
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
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

    private static SimpleClientHttpRequestFactory tiempos() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));  // un archivo grande en base64 tarda en subir
        return factory;
    }
}
