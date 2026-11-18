package com.uagrm.repositorio.dao.entity;

/**
 * Estado de un analisis de plagio (columna {@code analisis_plagio.estado}, CHECK en V11).
 * EN_PROCESO -> COMPLETADO | ERROR (ambos finales). No renombrar: el contrato plagio-api.yaml usa estos nombres.
 */
public enum EstadoAnalisisPlagio {
    EN_PROCESO,
    COMPLETADO,
    ERROR
}
