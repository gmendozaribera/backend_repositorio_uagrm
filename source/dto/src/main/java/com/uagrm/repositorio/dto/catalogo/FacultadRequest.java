package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** estado es opcional: en POST por defecto 1, en PUT si es null se conserva el actual. */
public record FacultadRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 20) String sigla,
        @Min(0) @Max(1) Integer estado) {
}
