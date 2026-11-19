package com.uagrm.repositorio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.RegistroRequest;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.service.AuthService;
import com.uagrm.repositorio.imp.service.RegistroService;
import com.uagrm.repositorio.mockup.UsuarioMock;
import com.uagrm.repositorio.mockup.integracion.ContenedoresIntegracion;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integracion end-to-end del padron con PostgreSQL y Redis reales (regla 9; se salta sin Docker): el admin importa
 * el CSV por HTTP (user-service), el estudiante se auto-registra con CI + registro (RegistroService, el mismo que
 * expone auth-service en /auth/register) e inicia sesion con la contrasena por defecto del padron (AuthService).
 * Se verifica el upsert de una segunda importacion, la cuenta duplicada (409) y la notificacion de bienvenida.
 */
@SpringBootTest(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.dominio.cifrado-key=ZGV2LXNvbG8tY2lmcmFkby1kb21pbmlvLTMyYnl0ZXM=",
        "app.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class PadronRegistroIntegracionTest extends ContenedoresIntegracion {

    private static final String CSV = """
            registro,ci,correo,password_default,nombres,apellidos,sigla_carrera
            219000003,7000003,luis.mamani@uagrm.edu.bo,Uagrm2026,Luis,Mamani Choque,INF
            219000004,7000004,eva.rios@uagrm.edu.bo,Uagrm2026,Eva,Rios Paz,INF
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RegistroService registroService;
    @Autowired
    private AuthService authService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void importarPadronYAutoRegistrarse() throws Exception {
        jdbc.update("INSERT INTO facultad (nombre, sigla) VALUES ('FICCT', 'FICCT')");
        jdbc.update("INSERT INTO carrera (nombre, sigla, id_facultad) "
                + "SELECT 'Ingenieria Informatica', 'INF', id_facultad FROM facultad WHERE sigla = 'FICCT'");

        // 1) El admin importa el padron (multipart real contra PostgreSQL)
        mvc.perform(multipart("/api/v1/padron/import").file(archivo()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFilas").value(2))
                .andExpect(jsonPath("$.data.insertados").value(2))
                .andExpect(jsonPath("$.data.conErrores").value(0));
        String hash = jdbc.queryForObject(
                "SELECT password_default FROM estudiante_habilitado WHERE registro = 219000003", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("Uagrm2026");

        // 2) Auto-registro con CI + registro: crea persona, usuario ESTUDIANTE y la notificacion de bienvenida
        assertThat(registroService.registrar(new RegistroRequest("7000003", 219000003), "10.0.0.1").usuario())
                .isEqualTo("219000003");
        Integer idUsuario = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE usuario = '219000003'",
                Integer.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE id_usuario = ?", Integer.class,
                idUsuario)).isEqualTo(1);

        // 3) Login con la contrasena por defecto del padron: debe cambiarla antes de continuar
        LoginResponse login = authService.login(new LoginRequest("219000003", "Uagrm2026"), "10.0.0.1");
        assertThat(login.tokens()).isNotNull();
        assertThat(login.tokens().usuario().requiereCambioPassword()).isTrue();

        // 4) El listado del padron ya lo muestra como registrado
        mvc.perform(get("/api/v1/padron").param("registrado", "true").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElementos").value(1))
                .andExpect(jsonPath("$.data.contenido[0].registro").value(219000003));

        // 5) Reimportar el mismo archivo actualiza (upsert), no duplica; registrarse otra vez es 409
        mvc.perform(multipart("/api/v1/padron/import").file(archivo()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insertados").value(0))
                .andExpect(jsonPath("$.data.actualizados").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM estudiante_habilitado", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> registroService.registrar(new RegistroRequest("7000003", 219000003), "10.0.0.1"))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        // el 409 cuenta como intento fallido de esa IP (limite anti-enumeracion del padron, en Redis)
        assertThat(redis.hasKey("auth:rate:register:10.0.0.1")).isTrue();
    }

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "padron.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
    }

    private String admin() {
        return "Bearer " + jwtService.generar(UsuarioMock.usuario(1, "admin", TipoUsuario.ADMIN), Duration.ofMinutes(5))
                .accessToken();
    }
}
