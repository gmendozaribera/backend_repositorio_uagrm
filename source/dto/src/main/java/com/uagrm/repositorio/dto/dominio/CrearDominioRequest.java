package com.uagrm.repositorio.dto.dominio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un parametro. grupo y nombre se guardan en MAYUSCULAS; lastUser y lastTime los pone el servidor. */
public record CrearDominioRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = ReglasDominio.PATRON_CODIGO, message = ReglasDominio.MENSAJE_CODIGO)
        String domainGroup,
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = ReglasDominio.PATRON_CODIGO, message = ReglasDominio.MENSAJE_CODIGO)
        String domainName,
        @NotBlank @Size(max = 500) String domainValue,
        @NotBlank @Size(max = 255) String description) {
}
