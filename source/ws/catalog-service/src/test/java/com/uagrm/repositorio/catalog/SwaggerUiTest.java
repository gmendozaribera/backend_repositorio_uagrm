package com.uagrm.repositorio.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uagrm.repositorio.catalog.config.SecurityConfig;
import com.uagrm.repositorio.catalog.controller.FacultadController;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.config.SwaggerUiConfig;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.CabecerasSeguridad;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.catalogo.FacultadService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger UI con el contrato (imp SwaggerUiConfig): apagado por defecto (las rutas caen en el denyAll del servicio)
 * y, encendido, publica solo el YAML de este servicio y los archivos de la UI, sin token. Tambien las cabeceras de
 * seguridad propias de la UI (CSP compatible) y HSTS cuando se enciende.
 */
class SwaggerUiTest {

    @WebMvcTest(controllers = FacultadController.class)
    @Import({SecurityConfig.class, SeguridadConfig.class, SeguridadErrorHandler.class, JwtService.class,
            SwaggerUiConfig.class})
    @TestPropertySource(properties = {
            "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
            "app.seguridad.jwt.emisor=repositorio-uagrm",
            "app.seguridad.jwt.refresh-ttl=7d",
            "app.seguridad.dos-factores.emisor=Repositorio UAGRM",
            "app.seguridad.dos-factores.ttl-pendiente=300s",
            "app.seguridad.dos-factores.max-intentos=5",
            "app.seguridad.cors-origenes=http://localhost:4200",
            "app.swagger-ui.contrato=catalog-api.yaml"
    })
    abstract static class Base {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        FacultadService facultadService;
        @MockitoBean
        RevocacionTokenService revocacionTokenService;
    }

    @Nested
    @TestPropertySource(properties = "app.swagger-ui.enabled=true")
    class Activado extends Base {

        @Test
        void publicaElContratoDelServicioSinToken() throws Exception {
            mvc.perform(get("/openapi/catalog-api.yaml"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("openapi: 3.0.3")));
        }

        @Test
        void noPublicaLosContratosDeOtrosServiciosNiOtrosMetodos() throws Exception {
            mvc.perform(get("/openapi/auth-api.yaml")).andExpect(status().isUnauthorized());
            mvc.perform(post("/openapi/catalog-api.yaml")).andExpect(status().isUnauthorized());
        }

        @Test
        void swaggerUiCargaElContrato() throws Exception {
            mvc.perform(get("/swagger-ui.html")).andExpect(redirectedUrl("/swagger-ui/index.html"));
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("swagger-initializer.js")));
            mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
            mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("url: '../openapi/catalog-api.yaml'")));
        }

        @Test
        void laApiSigueExigiendoToken() throws Exception {
            mvc.perform(get("/api/v1/facultades/1/config")).andExpect(status().isUnauthorized());
        }

        @Test
        void laUiTieneSuPropiaCspYLaApiLaEstricta() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(header().string("Content-Security-Policy", CabecerasSeguridad.CSP_SWAGGER_UI))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
            mvc.perform(get("/openapi/catalog-api.yaml"))
                    .andExpect(header().string("Content-Security-Policy", CabecerasSeguridad.CSP_SWAGGER_UI));
            mvc.perform(get("/api/v1/facultades/1/config"))
                    .andExpect(header().string("Content-Security-Policy", CabecerasSeguridad.CSP_API));
        }
    }

    /** HSTS encendido (SEGURIDAD_HSTS_ENABLED=true): en toda respuesta, API y Swagger UI. */
    @Nested
    @TestPropertySource(properties = {"app.swagger-ui.enabled=true", "app.seguridad.hsts-enabled=true"})
    class ConHsts extends Base {

        @Test
        void hstsDeUnAnoConSubdominios() throws Exception {
            String hsts = "max-age=31536000; includeSubDomains";
            mvc.perform(get("/api/v1/facultades/1/config"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Strict-Transport-Security", hsts));
            mvc.perform(get("/swagger-ui/index.html")).andExpect(header().string("Strict-Transport-Security", hsts));
        }
    }

    @Nested
    class DesactivadoPorDefecto extends Base {

        @Test
        void niLaUiNiElContratoSonAccesibles() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
            mvc.perform(get("/openapi/catalog-api.yaml")).andExpect(status().isUnauthorized());
        }
    }
}
