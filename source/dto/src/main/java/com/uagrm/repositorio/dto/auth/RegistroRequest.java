package com.uagrm.repositorio.dto.auth;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Auto-registro de estudiante: CI (texto, con complemento opcional) + numero de registro, validados contra el padron. */
public record RegistroRequest(
        @NotBlank
        @Size(min = 4, max = CarnetIdentidad.LONGITUD_MAXIMA)
        @Pattern(regexp = CarnetIdentidad.PATRON, message = CarnetIdentidad.MENSAJE)
        String ci,
        @NotNull @Positive Integer registro) {
}
