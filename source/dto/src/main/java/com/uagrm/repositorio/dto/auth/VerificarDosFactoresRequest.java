package com.uagrm.repositorio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Paso 2 del login: token temporal del paso 1 + codigo de Google Authenticator. */
public record VerificarDosFactoresRequest(
        @NotBlank String tokenTemporal,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 digitos") String codigo) {
}
