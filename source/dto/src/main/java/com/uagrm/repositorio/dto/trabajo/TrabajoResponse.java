package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import java.time.Instant;
import java.util.List;

/**
 * Detalle del trabajo con documentos, historial (del mas antiguo al mas reciente) y acciones del usuario actual.
 *
 * @param observaciones ultimas observaciones o motivo de rechazo del docente
 * @param calificacion  solo en EVALUADO (null en cualquier otro estado). Este detalle solo lo ven autores, docentes
 *                      del grupo y ADMIN; la calificacion NUNCA sale en el repositorio ni en el acceso anonimo
 * @param escalaCalifMax escala maxima vigente de la facultad (para el formulario de calificacion)
 * @param idFacultad    facultad de la materia (para consultar /documentos/reglas)
 */
public record TrabajoResponse(
        Integer idTrabajo,
        String titulo,
        String resumen,
        EstadoTrabajo estado,
        String observaciones,
        Integer calificacion,
        Integer escalaCalifMax,
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
        List<CambioEstadoResponse> historial,
        AccionesResponse acciones,
        Instant fechaCreacion,
        Instant fechaActualizacion,
        Instant fechaEnvio) {
}
