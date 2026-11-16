package com.uagrm.repositorio.dto.catalogo;

public record DocenteGrupoResponse(
        Integer idGrupo,
        Integer idUsuario,
        String usuario,
        String nombre,
        String apellidos,
        Boolean esAdminCurso) {
}
