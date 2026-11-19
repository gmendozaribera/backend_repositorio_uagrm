package com.uagrm.repositorio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uagrm.repositorio.mockup.integracion.ContenedoresIntegracion;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba DEL seed de demo (perfil demo activado a proposito solo en esta clase; el resto de tests nunca lo carga):
 * se aplica sobre V1..V12 con PostgreSQL real, es idempotente (re-aplicarlo no duplica nada), los usuarios demo y el
 * admin entran sin cambio de contrasena ni 2FA y el padron deja a 219000003 listo para el auto-registro.
 */
@SpringBootTest(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.seguridad.totp-cifrado-key=ZGV2LXNvbG8tY2lmcmFkby10b3RwLWF1dGgtMzJieXQ=",
        "app.admin-inicial.habilitado=true",
        "app.admin-inicial.password=Admin2026.",
        "app.swagger-ui.enabled=false"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoSeedIntegracionTest extends ContenedoresIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    @Test
    void seedCompletoEIdempotente() throws Exception {
        assertThat(contar("SELECT count(*) FROM carrera c JOIN facultad f USING (id_facultad) WHERE f.sigla = 'FICCT'"))
                .isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM grupo")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT acceso_externo AND NOT comparacion_internet AND escala_calif_max = 100 "
                + "AND tamano_max_archivo_mb = 10 FROM config_facultad", Boolean.class)).isTrue();
        assertThat(contar("SELECT count(*) FROM fase WHERE nombre = 'Perfil' AND estado = 1")).isEqualTo(1);
        assertThat(contar("""
                SELECT count(*) FROM grupo_docente gd JOIN usuario u USING (id_usuario) JOIN grupo g USING (id_grupo)
                JOIN materia m USING (id_materia)
                WHERE u.usuario = 'doc.maria' AND gd.es_admin_curso AND g.nombre = 'SA' AND m.sigla IN ('INF412', 'INF312')
                """)).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM trabajo WHERE estado = 'EVALUADO'")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM estudiante_habilitado")).isEqualTo(3);

        // Re-aplicar el repetible (como si cambiara su contenido) no duplica nada
        assertThat(contar("SELECT count(*) FROM flyway_schema_history WHERE script = 'R__demo_ficct.sql'"))
                .isEqualTo(1);
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/R__demo_ficct.sql")).execute(dataSource);
        assertThat(contar("SELECT count(*) FROM trabajo")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM documento")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM usuario")).isEqualTo(5);
        assertThat(contar("SELECT count(*) FROM trabajo_hashtag")).isEqualTo(3);

        // Logins: los 4 demo con Demo2026! y el admin con su contrasena, todos sin pasos pendientes
        for (String usuario : new String[] {"doc.maria", "doc.jose", "219000001", "219000002"}) {
            login(usuario, "Demo2026!");
        }
        login("admin", "Admin2026.");
    }

    private void login(String usuario, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiereDosFactores").value(false))
                .andExpect(jsonPath("$.data.tokens.usuario.requiereCambioPassword").value(false));
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
