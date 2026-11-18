package com.uagrm.repositorio.imp.service.plagio;

import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import java.math.BigDecimal;

/**
 * Una coincidencia que entrega el motor.
 *
 * @param titulo          titulo de la fuente (de un trabajo interno: su titulo real; al responder se oculta si no
 *                        esta publicado)
 * @param url             solo INTERNET
 * @param idTrabajoFuente solo INTERNA, si se pudo identificar el trabajo
 * @param porcentaje      0..100
 */
public record FuenteDetectada(TipoFuentePlagio tipo, String titulo, String url, Integer idTrabajoFuente,
                              BigDecimal porcentaje) {
}
