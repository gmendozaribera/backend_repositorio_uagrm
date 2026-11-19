package com.uagrm.repositorio.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.mockup.UsuarioMock;
import com.uagrm.repositorio.mockup.integracion.ContenedoresIntegracion;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cache de catalogos con Redis real (regla 9; se salta sin Docker): miss -> se guarda con TTL; hit -> se responde
 * desde Redis sin mirar PostgreSQL (se prueba cambiando la fila por SQL directo: la respuesta sigue siendo la
 * cacheada); un PUT invalida la key y su indice tras el commit y el siguiente GET lee el dato nuevo.
 */
@SpringBootTest(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class CatalogoCacheIntegracionTest extends ContenedoresIntegracion {

    private static final String CLAVE = "catalog:cache:facultades:todas";
    private static final String INDICE = "catalog:cache:facultades:indice";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void missHitEInvalidacionTrasPut() throws Exception {
        Integer id = jdbc.queryForObject(
                "INSERT INTO facultad (nombre, sigla) VALUES ('Facultad de Tecnologia', 'FCT') RETURNING id_facultad",
                Integer.class);
        assertThat(redis.hasKey(CLAVE)).isFalse();

        // Miss: lee PostgreSQL y guarda el listado con TTL explicito, registrado en el indice de la entidad
        listarFacultades().andExpect(jsonPath("$.data[0].nombre").value("Facultad de Tecnologia"));
        assertThat(redis.hasKey(CLAVE)).isTrue();
        assertThat(redis.getExpire(CLAVE)).isPositive();
        assertThat(redis.opsForSet().members(INDICE)).containsExactly(CLAVE);

        // Hit: un cambio hecho por fuera de la API no se ve mientras la key exista
        jdbc.update("UPDATE facultad SET nombre = 'Cambiada por SQL' WHERE id_facultad = ?", id);
        listarFacultades().andExpect(jsonPath("$.data[0].nombre").value("Facultad de Tecnologia"));

        // Escritura por la API: invalida la key y el indice; el siguiente GET vuelve a PostgreSQL
        mvc.perform(put("/api/v1/facultades/{id}", id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Facultad de Ciencias y Tecnologia\",\"sigla\":\"FCYT\"}"))
                .andExpect(status().isOk());
        assertThat(redis.hasKey(CLAVE)).isFalse();
        assertThat(redis.hasKey(INDICE)).isFalse();
        listarFacultades()
                .andExpect(jsonPath("$.data[0].nombre").value("Facultad de Ciencias y Tecnologia"))
                .andExpect(jsonPath("$.data[0].sigla").value("FCYT"));
        assertThat(redis.hasKey(CLAVE)).isTrue();
    }

    private ResultActions listarFacultades() throws Exception {
        return mvc.perform(get("/api/v1/facultades").header("Authorization", admin())).andExpect(status().isOk());
    }

    private String admin() {
        return "Bearer " + jwtService.generar(UsuarioMock.usuario(1, "admin", TipoUsuario.ADMIN), Duration.ofMinutes(5))
                .accessToken();
    }
}
