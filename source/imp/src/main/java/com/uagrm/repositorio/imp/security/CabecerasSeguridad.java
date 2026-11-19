package com.uagrm.repositorio.imp.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Cabeceras HTTP de seguridad, iguales en los 5 servicios. Cada SecurityFilterChain las aplica con
 * {@code http.headers(cabeceras.api())}; la cadena de Swagger UI con {@link #swaggerUi()}. Al ir en el
 * SecurityFilterChain llegan a TODA respuesta: 200, 401/403 de seguridad, 429/503 de los filtros y errores.
 *
 * <p>Que hace cada una, en simple (son instrucciones para el NAVEGADOR):</p>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}: "cree en el Content-Type que te digo". Sin ella un navegador podria
 *       "adivinar" que un JSON o un archivo subido es HTML/JS y ejecutarlo.</li>
 *   <li>{@code X-Frame-Options: DENY} y {@code frame-ancestors 'none'}: nadie puede meter estas respuestas dentro de
 *       un iframe de otra pagina (evita el "clickjacking": un boton invisible encima de otro).</li>
 *   <li>{@code Referrer-Policy: no-referrer}: al seguir un enlace no se envia la URL de origen (que podria llevar ids
 *       o parametros) a sitios de terceros.</li>
 *   <li>{@code Cache-Control: no-store}: ni el navegador ni un proxy guardan copia de las respuestas (datos personales,
 *       tokens, archivos privados). Solo se pone si la respuesta no trae ya su propia politica.</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'; frame-ancestors 'none'}: la API solo devuelve datos, nunca
 *       paginas; si alguien lograra que el navegador abriera una respuesta como pagina, no podria cargar ni ejecutar
 *       nada. Swagger UI si es una pagina: su CSP permite solo scripts, estilos e imagenes del mismo servidor.</li>
 *   <li>HSTS ({@code Strict-Transport-Security}), solo si {@code app.seguridad.hsts-enabled=true}
 *       (SEGURIDAD_HSTS_ENABLED): "durante un ano, entra a este dominio SIEMPRE por https". Apagado por defecto porque
 *       en produccion lo pone el Ingress, que es quien tiene el certificado TLS; en local (http) no tiene sentido.</li>
 * </ul>
 */
public class CabecerasSeguridad {

    /** La API devuelve JSON (o archivos): no hay nada que cargar ni ejecutar. */
    public static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";
    /** Swagger UI: sus scripts, estilos (con inline, los usa la UI) e imagenes (incluye data:) del mismo servidor. */
    public static final String CSP_SWAGGER_UI =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; "
                    + "frame-ancestors 'none'";
    public static final String NO_STORE = "no-store";
    public static final String HSTS = "Strict-Transport-Security";
    /** Un ano, tambien para los subdominios. */
    public static final String HSTS_VALOR = "max-age=31536000; includeSubDomains";

    private final boolean hstsActivo;

    public CabecerasSeguridad(boolean hstsActivo) {
        this.hstsActivo = hstsActivo;
    }

    /** Cabeceras de la API (todas las cadenas de los servicios). */
    public Customizer<HeadersConfigurer<HttpSecurity>> api() {
        return headers -> comunes(headers, CSP_API);
    }

    /** Cabeceras de la cadena de Swagger UI: iguales salvo la CSP. */
    public Customizer<HeadersConfigurer<HttpSecurity>> swaggerUi() {
        return headers -> comunes(headers, CSP_SWAGGER_UI);
    }

    private void comunes(HeadersConfigurer<HttpSecurity> headers, String csp) {
        headers.contentTypeOptions(Customizer.withDefaults())
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(politica -> politica.policyDirectives(csp))
                // El de Spring escribe "no-cache, no-store, max-age=0, must-revalidate" + Pragma + Expires;
                // basta no-store (lo entienden todos los navegadores y proxies HTTP/1.1)
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                .addHeaderWriter(noStoreSiNoHayPolitica());
        // El HSTS de Spring solo se escribe en requests https; con TLS terminado en el Ingress el request llega por http
        // al pod, asi que se usa una cabecera fija (el navegador solo la respeta si la recibio por https: enviarla
        // siempre no tiene riesgo)
        headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
        if (hstsActivo) {
            headers.addHeaderWriter(new StaticHeadersWriter(HSTS, HSTS_VALOR));
        }
    }

    /**
     * {@code Cache-Control: no-store} salvo que el controller ya haya puesto su propia politica (hoy ninguno lo hace:
     * tambien las descargas de archivos quedan no-store, que es lo correcto para documentos privados).
     */
    private static HeaderWriter noStoreSiNoHayPolitica() {
        return (request, response) -> {
            if (!response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
            }
        };
    }
}
