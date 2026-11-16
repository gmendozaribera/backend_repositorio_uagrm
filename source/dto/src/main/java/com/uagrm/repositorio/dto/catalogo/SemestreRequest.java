package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SemestreRequest(
        @NotNull @Min(1) @Max(4) Integer semestre,
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Positive Integer idFacultad,
        @Min(0) @Max(1) Integer estado) {
}
