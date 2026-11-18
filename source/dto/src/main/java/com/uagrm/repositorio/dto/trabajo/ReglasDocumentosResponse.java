package com.uagrm.repositorio.dto.trabajo;

import java.util.List;

/**
 * Formatos, tamanos y hosts vigentes para una facultad; el frontend valida con esto antes de subir.
 *
 * @param maxAutores maximo de autores por trabajo, incluido el autor (WORK_GROUP.MAX_AUTORES)
 */
public record ReglasDocumentosResponse(Integer idFacultad, List<FormatoCategoriaResponse> formatos,
                                       int subidasPorMinuto, int maxAutores) {
}
