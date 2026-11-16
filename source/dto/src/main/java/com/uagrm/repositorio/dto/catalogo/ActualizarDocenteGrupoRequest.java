package com.uagrm.repositorio.dto.catalogo;

import jakarta.validation.constraints.NotNull;

/** Cambia solo el flag esAdminCurso de una asignacion docente-grupo existente. */
public record ActualizarDocenteGrupoRequest(@NotNull Boolean esAdminCurso) {
}
