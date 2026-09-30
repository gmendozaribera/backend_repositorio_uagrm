package com.uagrm.repositorio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambioPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "debe contener al menos una mayuscula, una minuscula y un numero")
        String passwordNueva) {
}
