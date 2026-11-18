package com.uagrm.repositorio.dto.trabajo;

import java.time.Instant;
import java.util.List;

/**
 * Ficha publica de un trabajo EVALUADO (repositorio). A proposito NO lleva calificacion, observaciones, historial
 * ni acciones: eso es solo para autores y docentes (detalle interno /trabajos/{id}).
 *
 * @param periodo          semestre y anio, p. ej. 2-2026
 * @param idFacultad       facultad de la materia
 * @param fechaPublicacion fecha en que paso a EVALUADO (ultima actualizacion si no hay historial de esa transicion)
 */
public record TrabajoPublicoResponse(
        Integer idTrabajo,
        String titulo,
        String resumen,
        Integer idGrupo,
        String grupo,
        Integer idMateria,
        String siglaMateria,
        String nombreMateria,
        Integer idSemestre,
        String periodo,
        Integer idFacultad,
        Integer idTipoTrabajo,
        String tipoTrabajo,
        List<AutorResponse> autores,
        List<String> hashtags,
        List<DocumentoResponse> documentos,
        Instant fechaPublicacion) {
}
