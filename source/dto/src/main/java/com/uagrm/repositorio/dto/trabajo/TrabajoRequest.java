package com.uagrm.repositorio.dto.trabajo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Crear o editar un trabajo. Materia y semestre no se envian: salen del grupo.
 *
 * @param hashtags           sin '#'; se guardan en minusculas (duplicados ignorados)
 * @param registrosCoautores registros universitarios de OTROS estudiantes (el autor autenticado siempre queda)
 */
public record TrabajoRequest(
        @NotBlank @Size(min = 5, max = 200) String titulo,
        @NotBlank @Size(min = 20, max = 3000) String resumen,
        @NotNull @Positive Integer idGrupo,
        @NotNull @Positive Integer idTipoTrabajo,
        @Size(max = 10) List<@NotNull @Size(min = 2, max = 50) @Pattern(regexp = "^[A-Za-z0-9_\\-]+$") String> hashtags,
        @Size(max = 10) List<@NotNull @Positive Integer> registrosCoautores) {
}
