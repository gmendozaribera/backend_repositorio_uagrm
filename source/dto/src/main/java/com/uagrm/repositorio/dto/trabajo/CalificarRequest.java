package com.uagrm.repositorio.dto.trabajo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Calificacion del trabajo. El minimo (0) se valida aqui; el maximo depende de la facultad
 * (ConfigFacultad.escalaCalifMax) y lo valida el service.
 */
public record CalificarRequest(
        @NotNull @Min(value = 0, message = "debe ser mayor o igual a 0") Integer calificacion) {
}
