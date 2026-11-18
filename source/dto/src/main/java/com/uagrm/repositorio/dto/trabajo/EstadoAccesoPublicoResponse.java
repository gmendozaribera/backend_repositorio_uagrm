package com.uagrm.repositorio.dto.trabajo;

import java.util.List;

/**
 * Estado del acceso anonimo al repositorio (GET /api/v1/publico/estado).
 *
 * @param habilitado  true si al menos una facultad tiene acceso externo
 * @param facultades  ids de las facultades con acceso externo habilitado
 */
public record EstadoAccesoPublicoResponse(boolean habilitado, List<Integer> facultades) {
}
