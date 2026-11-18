package com.uagrm.repositorio.dto.plagio;

import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;

/** Estado liviano del ultimo analisis (schema EstadoAnalisisResumen de plagio-api.yaml). */
public record EstadoAnalisisPlagioResponse(Integer idAnalisis, EstadoAnalisisPlagio estado,
                                           Double porcentajeSimilitud, NivelPlagio nivel) {
}
