package com.uagrm.repositorio.dto.plagio;

import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;

/**
 * Fuente de un analisis (schema Fuente de plagio-api.yaml).
 *
 * @param url       solo INTERNET
 * @param idTrabajo solo INTERNA publicada (EVALUADO); null en las no publicadas
 */
public record FuentePlagioResponse(TipoFuentePlagio tipo, String titulo, String url, Integer idTrabajo,
                                   Double porcentaje) {
}
