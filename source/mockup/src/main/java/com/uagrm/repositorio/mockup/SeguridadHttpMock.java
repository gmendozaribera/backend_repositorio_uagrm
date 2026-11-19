package com.uagrm.repositorio.mockup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Comprobaciones comunes de hardening HTTP para los ContratoTest de los 5 servicios: cabeceras de seguridad
 * (imp CabecerasSeguridad), CORS (imp SeguridadConfig) y errores 500 sin detalles internos. Los valores esperados
 * se repiten aqui a proposito (este modulo no depende de imp): si alguien cambia una cabecera, el test lo nota.
 */
public final class SeguridadHttpMock {

    public static final String ORIGEN_PERMITIDO = "http://localhost:4200";
    public static final String ORIGEN_AJENO = "https://sitio-malicioso.example";
    /** Mensaje "interno" que un 500 jamas debe devolver al cliente. */
    public static final String MENSAJE_INTERNO = "jdbc:postgresql://db-interna:5432 password=secreta";

    private SeguridadHttpMock() {
    }

    /** Cabeceras de seguridad de una respuesta de la API (sin HSTS: apagado por defecto). */
    public static ResultMatcher cabecerasApi() {
        return todos(
                header().string("X-Content-Type-Options", "nosniff"),
                header().string("X-Frame-Options", "DENY"),
                header().string("Referrer-Policy", "no-referrer"),
                header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"),
                header().doesNotExist("Strict-Transport-Security"));
    }

    /** Preflight CORS (OPTIONS) de un navegador en {@code origen} que quiere hacer {@code metodo} con token. */
    public static MockHttpServletRequestBuilder preflight(String url, String origen, String metodo) {
        return options(url)
                .header(HttpHeaders.ORIGIN, origen)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, metodo)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
    }

    /** El origen del frontend recibe permiso, sin credenciales (no hay cookies) y con cache del preflight. */
    public static ResultMatcher preflightPermitido() {
        return todos(
                status().isOk(),
                header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO),
                header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS),
                header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    /** Cualquier otro origen: 403 y sin Access-Control-Allow-Origin (el navegador bloquea la llamada). */
    public static ResultMatcher preflightRechazado() {
        return todos(
                status().isForbidden(),
                header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /** Respuesta real (no preflight) desde el frontend: solo expone Retry-After y Content-Disposition. */
    public static ResultMatcher corsExponeSoloLoNecesario() {
        return todos(
                header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO),
                header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After, Content-Disposition"));
    }

    /** 500 generico: ProblemDetail sin el mensaje de la excepcion, sin traza y con las cabeceras de seguridad. */
    public static ResultMatcher error500SinDetallesInternos() {
        return todos(
                status().isInternalServerError(),
                cabecerasApi(),
                resultado -> {
                    String cuerpo = cuerpo(resultado);
                    afirmar(cuerpo.contains("Ocurrio un error inesperado"), "detail generico", cuerpo);
                    for (String prohibido : new String[] {MENSAJE_INTERNO, "jdbc", "password", "trace",
                            "exception", "RuntimeException", "at com."}) {
                        afirmar(!cuerpo.contains(prohibido), "sin '" + prohibido + "'", cuerpo);
                    }
                });
    }

    /** Todas las comprobaciones (Spring 7 ya no trae ResultMatcher.matchAll). */
    private static ResultMatcher todos(ResultMatcher... comprobaciones) {
        return resultado -> {
            for (ResultMatcher comprobacion : comprobaciones) {
                comprobacion.match(resultado);
            }
        };
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static void afirmar(boolean condicion, String que, String cuerpo) {
        if (!condicion) {
            throw new AssertionError("Respuesta 500 incorrecta (" + que + "): " + cuerpo);
        }
    }
}
