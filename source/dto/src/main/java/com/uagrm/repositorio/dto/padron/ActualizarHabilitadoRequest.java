package com.uagrm.repositorio.dto.padron;

import jakarta.validation.constraints.NotNull;

public record ActualizarHabilitadoRequest(@NotNull Boolean habilitado) {
}
