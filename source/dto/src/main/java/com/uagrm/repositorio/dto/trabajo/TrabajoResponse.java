package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import java.time.Instant;
import java.util.List;

/**
 * Detalle del trabajo con documentos, historial (del mas antiguo al mas reciente) y acciones del usuario actual.
 *
 * @param observaciones ultimas observaciones o motivo de rechazo del docente
 * @param idFacultad    facultad de la materia (para consultar /documentos/reglas)
 */
public record TrabajoResponse(
        Integer idTrabajo,
        String titulo,
        String resumen,
        EstadoTrabajo estado,
        String observaciones,
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
