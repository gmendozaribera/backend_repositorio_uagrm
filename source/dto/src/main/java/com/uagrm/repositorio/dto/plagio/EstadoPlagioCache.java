package com.uagrm.repositorio.dto.plagio;

import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;

/**
 * JSON corto del ultimo analisis de un trabajo guardado en Redis ({@code plagio:status:{idTrabajo}}, TTL 600 s):
 * lo que necesita {@code GET .../estado} sin tocar las tablas de plagio.
 */
public record EstadoPlagioCache(Integer idAnalisis, EstadoAnalisisPlagio estado, Double porcentaje,
                                NivelPlagio nivel) {
}
