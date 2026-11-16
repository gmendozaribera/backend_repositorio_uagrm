package com.uagrm.repositorio.dto.dominio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT de un parametro: solo valor y descripcion (grupo y nombre son inmutables, el codigo los referencia).
 *
 * @param domainValue omitido o null CONSERVA el valor actual (permite cambiar solo la descripcion, incluso de un
 *                    secreto, sin borrarlo). '' lo deja vacio solo si el parametro lo admite (lo valida el service).
 *                    En un secreto es el valor nuevo en texto plano (se cifra al guardar).
 */
public record ActualizarDominioRequest(
        @Size(max = 500) String domainValue,
        @NotBlank @Size(max = 255) String description) {
}
