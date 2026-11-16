package com.uagrm.repositorio.dto.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Auto-registro de estudiante: CI + numero de registro, validados contra el padron. */
public record RegistroRequest(
        @NotNull @Positive Integer ci,
        @NotNull @Positive Integer registro) {
}
