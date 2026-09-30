package com.uagrm.repositorio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodigoTotpRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 digitos") String codigo) {
}
