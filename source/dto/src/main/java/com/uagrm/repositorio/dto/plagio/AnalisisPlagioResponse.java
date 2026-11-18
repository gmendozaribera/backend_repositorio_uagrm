package com.uagrm.repositorio.dto.plagio;

import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import java.time.Instant;
import java.util.List;

/**
 * Analisis de plagio (schema Analisis de plagio-api.yaml).
 *
 * @param porcentajeSimilitud solo en COMPLETADO: el maximo de sus documentos
 * @param nivel               solo en COMPLETADO: semaforo segun umbralMedio / umbralAlto
 * @param iniciadoPor         nombre y apellidos de quien lo inicio
 */
public record AnalisisPlagioResponse(
        Integer idAnalisis,
        Integer idTrabajo,
        EstadoAnalisisPlagio estado,
        TipoMotorPlagio motor,
        Double porcentajeSimilitud,
        NivelPlagio nivel,
        Integer umbralMedio,
        Integer umbralAlto,
        Boolean comparacionInternet,
        List<FuentePlagioResponse> fuentes,
        Integer documentosAnalizados,
        String mensajeError,
        String iniciadoPor,
        Instant fechaInicio,
        Instant fechaFin) {
}
