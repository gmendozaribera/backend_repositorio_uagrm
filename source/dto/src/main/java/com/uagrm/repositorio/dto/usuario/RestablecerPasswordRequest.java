package com.uagrm.repositorio.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestablecerPasswordRequest(
        @NotBlank
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = ReglasUsuario.PATRON_PASSWORD, message = ReglasUsuario.MENSAJE_PASSWORD)
        String passwordNueva) {
}
