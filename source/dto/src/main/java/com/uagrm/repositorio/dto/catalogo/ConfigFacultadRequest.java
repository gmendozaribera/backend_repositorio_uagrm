package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/v1/facultades/{id}/config. Rangos iguales al contrato y a los CHECK de config_facultad (V1).
 * La regla umbralPlagioMedio menor que umbralPlagioAlto la valida el service (400 con errores).
 */
public record ConfigFacultadRequest(
        @NotNull @Min(1) @Max(100) Integer tamanoMaxArchivoMB,
        @NotNull @Min(0) @Max(100) Integer umbralPlagioMedio,
        @NotNull @Min(0) @Max(100) Integer umbralPlagioAlto,
        @NotNull Boolean comparacionInternet,
        @NotNull Boolean accesoExterno,
        @NotNull @Min(1) @Max(1000) Integer escalaCalifMax,
        @NotNull @Min(1) @Max(24) Integer jwtExpiracionHoras) {
}
