package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** descripcion es opcional; en PUT un null la borra. */
public record FaseRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 255) String descripcion,
        @NotNull @Positive Integer idFacultad,
        @Min(0) @Max(1) Integer estado) {
}
