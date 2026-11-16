package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GrupoRequest(
        @NotBlank @Size(max = 20) String nombre,
        @NotNull @Positive Integer idMateria,
        @NotNull @Positive Integer idSemestre,
        @Min(0) @Max(1) Integer estado) {
}
