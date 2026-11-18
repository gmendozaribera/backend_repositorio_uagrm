package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import java.time.Instant;
import java.util.List;

/**
 * Fila de los listados ("mis trabajos" y bandeja de revision).
 *
 * @param periodo semestre y anio, p. ej. 2-2026
 * @param autores nombres completos
 */
public record TrabajoResumenResponse(
        Integer idTrabajo,
        String titulo,
        EstadoTrabajo estado,
        Integer idGrupo,
        String grupo,
        Integer idMateria,
        String siglaMateria,
        String nombreMateria,
        Integer idSemestre,
        String periodo,
        String tipoTrabajo,
        List<String> autores,
        Instant fechaActualizacion,
        Instant fechaEnvio) {
}
