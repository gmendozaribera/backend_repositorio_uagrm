package com.uagrm.repositorio.dto.trabajo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarRequest(@NotBlank @Size(min = 10, max = 3000) String motivo) {
}
