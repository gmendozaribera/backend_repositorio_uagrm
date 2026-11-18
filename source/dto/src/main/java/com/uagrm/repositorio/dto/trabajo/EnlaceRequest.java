package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Enlace externo. Solo categorias VIDEO y CODIGO, solo https y hosts permitidos (lo valida el service). */
public record EnlaceRequest(
        @NotBlank @Size(max = 200) String titulo,
        @Size(max = 500) String descripcion,
        @NotBlank @Size(max = 500) String url,
        @NotNull CategoriaDocumento categoria,
        @NotNull @Positive Integer idFase) {
}
