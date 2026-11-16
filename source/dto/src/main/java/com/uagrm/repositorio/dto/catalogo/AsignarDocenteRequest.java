package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AsignarDocenteRequest(
        @NotNull @Positive Integer idUsuario,
        @NotNull Boolean esAdminCurso) {
}
