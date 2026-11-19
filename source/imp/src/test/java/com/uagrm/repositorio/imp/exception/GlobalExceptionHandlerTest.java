package com.uagrm.repositorio.imp.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Errores del cliente: todos los 400 de validacion salen con el mismo formato (title "Datos invalidos" y el mapa
 * {@code errores} campo -> mensaje), los lance Spring MVC por el camino que sea. Errores que Tomcat detecta antes
 * del controller no deben llegar como 500.
 */
class GlobalExceptionHandlerTest {

    /** Controller de prueba con las mismas formas que los reales (calificar, listados paginados, alta). */
    @RestController
    static class ControllerDePrueba {

        record Calificacion(@NotNull @PositiveOrZero Integer calificacion) {
        }

        /** Restriccion en el path + body validado: Spring lanza HandlerMethodValidationException. */
        @PatchMapping("/trabajos/{id}/calificar")
        Map<String, Object> calificar(@PathVariable @Positive Integer id, @Valid @RequestBody Calificacion request) {
            return Map.of("id", id);
        }

        /** Solo body validado: Spring lanza MethodArgumentNotValidException. */
        @PostMapping("/trabajos")
        Map<String, Object> crear(@Valid @RequestBody Calificacion request) {
            return Map.of();
        }

        @GetMapping("/trabajos")
        Map<String, Object> listar(@RequestParam(name = "tamano", defaultValue = "20") @Max(100) int tamano,
                                   @RequestParam(name = "idGrupo") Integer idGrupo) {
            return Map.of();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ControllerDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void parametroQueNoEsUtf8Devuelve400() {
        ProblemDetail problem = new GlobalExceptionHandler()
                .handleParametroIlegible(new InvalidParameterException("Character decoding failed"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Datos invalidos");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void bodyInvalidoConPathValidadoTraeErroresPorCampo() throws Exception {
        datosInvalidos(patchJson("/trabajos/7/calificar", "{\"calificacion\":-5}"))
                .andExpect(jsonPath("$.errores.calificacion").isString())
                .andExpect(jsonPath("$.errores", aMapWithSize(1)));
        datosInvalidos(patchJson("/trabajos/7/calificar", "{}"))
                .andExpect(jsonPath("$.errores.calificacion").isString());
    }

    @Test
    void pathInvalidoTraeElNombreDelParametro() throws Exception {
        datosInvalidos(patchJson("/trabajos/0/calificar", "{\"calificacion\":80}"))
                .andExpect(jsonPath("$.errores.id").isString())
                .andExpect(jsonPath("$.errores", aMapWithSize(1)));
        // path y body invalidos a la vez: ambos en el mapa
        datosInvalidos(patchJson("/trabajos/-1/calificar", "{\"calificacion\":-1}"))
                .andExpect(jsonPath("$.errores.id").isString())
                .andExpect(jsonPath("$.errores.calificacion").isString());
    }

    @Test
    void bodyInvalidoSinRestriccionesEnParametrosMismoFormato() throws Exception {
        datosInvalidos(mvc.perform(post("/trabajos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calificacion\":-1}")))
                .andExpect(jsonPath("$.errores.calificacion").isString());
    }

    @Test
    void queryFueraDeRangoAusenteOConTipoIncorrecto() throws Exception {
        datosInvalidos(mvc.perform(get("/trabajos").param("tamano", "101").param("idGrupo", "4")))
                .andExpect(jsonPath("$.errores.tamano").isString());
        datosInvalidos(mvc.perform(get("/trabajos")))
                .andExpect(jsonPath("$.errores.idGrupo").value("es obligatorio"));
        datosInvalidos(mvc.perform(get("/trabajos").param("idGrupo", "abc")))
                .andExpect(jsonPath("$.errores.idGrupo").value("tiene un formato invalido"));
        datosInvalidos(patchJson("/trabajos/xyz/calificar", "{\"calificacion\":1}"))
                .andExpect(jsonPath("$.errores.id").value("tiene un formato invalido"));
    }

    @Test
    void jsonMalFormadoDevuelve400DatosInvalidos() throws Exception {
        patchJson("/trabajos/7/calificar", "{calif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos invalidos"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void constraintViolationFueraDeMvcMismoFormato() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var violaciones = validator.validate(new ControllerDePrueba.Calificacion(-3));
            ProblemDetail problem = new GlobalExceptionHandler()
                    .handleRestricciones(new ConstraintViolationException(violaciones));
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getTitle()).isEqualTo("Datos invalidos");
            assertThat(problem.getProperties()).containsKeys("timestamp", "errores");
            assertThat(problem.getProperties().get("errores")).asString().isEqualTo(
                    "{calificacion=" + violaciones.iterator().next().getMessage() + "}");
        }
    }

    private ResultActions patchJson(String url, String json) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ResultActions datosInvalidos(ResultActions resultado) throws Exception {
        return resultado.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos invalidos"))
                .andExpect(jsonPath("$.detail").value("Uno o mas campos no son validos."))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
