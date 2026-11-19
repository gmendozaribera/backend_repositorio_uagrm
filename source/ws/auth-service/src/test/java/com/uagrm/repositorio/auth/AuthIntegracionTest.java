package com.uagrm.repositorio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.uagrm.repositorio.mockup.integracion.ContenedoresIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integracion de auth-service con PostgreSQL y Redis reales (regla 9; se salta sin Docker): Flyway V1..V12, admin
 * inicial, login, cambio de contrasena obligatorio, refresh con rotacion, logout y revocacion en Redis
 * (auth:blacklist:{jti} y auth:revocado-desde:{idUsuario}).
 */
@SpringBootTest(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.admin-inicial.habilitado=true",
        "app.admin-inicial.password=Admin2026.",
        "app.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class AuthIntegracionTest extends ContenedoresIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cicloCompletoDeSesionConRevocacionReal() throws Exception {
        // Flyway aplico todas las migraciones y el admin inicial existe con cambio de contrasena obligatorio
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isGreaterThanOrEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT requiere_cambio_password FROM usuario WHERE usuario = 'admin'",
                Boolean.class)).isTrue();

        // Credenciales incorrectas: 401 y el intento queda contado en Redis
        llamar(post("/api/v1/auth/login"), null, "{\"usuario\":\"admin\",\"password\":\"incorrecta\"}")
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("auth:rate:login:127.0.0.1")).isTrue();

        // 1) Login: tokens de un usuario que debe cambiar la contrasena
        String login = llamar(post("/api/v1/auth/login"), null, "{\"usuario\":\"admin\",\"password\":\"Admin2026.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiereDosFactores").value(false))
                .andReturn().getResponse().getContentAsString();
        String access1 = JsonPath.read(login, "$.data.tokens.accessToken");
        String refresh1 = JsonPath.read(login, "$.data.tokens.refreshToken");

        // 2) Cambio de contrasena: tokens nuevos y TODAS las sesiones previas revocadas (revocado-desde)
        Thread.sleep(1100); // iat tiene resolucion de segundos: los tokens nuevos deben ser posteriores al corte
        String cambio = llamar(post("/api/v1/auth/password/change"), access1,
                "{\"passwordActual\":\"Admin2026.\",\"passwordNueva\":\"NuevaClave2026\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String access2 = JsonPath.read(cambio, "$.data.accessToken");
        String refresh2 = JsonPath.read(cambio, "$.data.refreshToken");
        assertThat(redis.keys("auth:revocado-desde:*")).hasSize(1);
        refrescar(refresh1).andExpect(status().isUnauthorized());
        llamar(post("/api/v1/auth/logout"), access1, null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT requiere_cambio_password FROM usuario WHERE usuario = 'admin'",
                Boolean.class)).isFalse();

        // 3) Refresh con rotacion: el refresh usado queda en la blacklist y no se puede reutilizar
        String renovado = refrescar(refresh2).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access3 = JsonPath.read(renovado, "$.data.accessToken");
        String refresh3 = JsonPath.read(renovado, "$.data.refreshToken");
        assertThat(redis.keys("auth:blacklist:*")).hasSize(1);
        refrescar(refresh2).andExpect(status().isUnauthorized());

        // 4) Logout: access y refresh revocados (con TTL = vida restante del token)
        llamar(post("/api/v1/auth/logout"), access3, "{\"refreshToken\":\"" + refresh3 + "\"}")
                .andExpect(status().isOk());
        assertThat(redis.keys("auth:blacklist:*")).hasSize(3);
        redis.keys("auth:blacklist:*").forEach(clave -> assertThat(redis.getExpire(clave)).isPositive());
        refrescar(refresh3).andExpect(status().isUnauthorized());
        llamar(post("/api/v1/auth/logout"), access3, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La sesion fue cerrada. Inicie sesion nuevamente."));
        // access2 no se cerro con logout: sigue valido hasta su expiracion
        llamar(post("/api/v1/auth/logout"), access2, null).andExpect(status().isOk());

        // 5) La contrasena nueva quedo en PostgreSQL
        llamar(post("/api/v1/auth/login"), null, "{\"usuario\":\"admin\",\"password\":\"NuevaClave2026\"}")
                .andExpect(status().isOk());
    }

    private ResultActions refrescar(String refreshToken) throws Exception {
        return llamar(post("/api/v1/auth/refresh"), null, "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    private ResultActions llamar(MockHttpServletRequestBuilder request, String accessToken, String cuerpo)
            throws Exception {
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (cuerpo != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(request);
    }
}
