package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CarreraRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 20) String sigla,
        @NotNull @Positive Integer idFacultad,
        @Min(0) @Max(1) Integer estado) {
}
