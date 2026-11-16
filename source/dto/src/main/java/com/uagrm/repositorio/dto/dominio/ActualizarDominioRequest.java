package com.uagrm.repositorio.dto.dominio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT de un parametro: solo valor y descripcion (grupo y nombre son inmutables, el codigo los referencia). */
public record ActualizarDominioRequest(
        @NotBlank @Size(max = 500) String domainValue,
        @NotBlank @Size(max = 255) String description) {
}
