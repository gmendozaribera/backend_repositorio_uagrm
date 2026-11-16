package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TipoTrabajoRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull @Positive Integer idFacultad,
        @Min(0) @Max(1) Integer estado) {
}
