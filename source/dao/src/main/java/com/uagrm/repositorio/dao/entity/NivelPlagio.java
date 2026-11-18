package com.uagrm.repositorio.dao.entity;

import java.math.BigDecimal;

/**
 * Semaforo del resultado segun los umbrales de la facultad:
 * {@code OK} (verde) &lt; umbralMedio &lt;= {@code MEDIO} (amarillo) &lt; umbralAlto &lt;= {@code ALTO} (rojo).
 */
public enum NivelPlagio {
    OK,
    MEDIO,
    ALTO;

    /** Nivel de un porcentaje con los umbrales dados (medio &lt; alto, ambos 0..100). */
    public static NivelPlagio de(BigDecimal porcentaje, int umbralMedio, int umbralAlto) {
        if (porcentaje.compareTo(BigDecimal.valueOf(umbralAlto)) >= 0) {
            return ALTO;
        }
        if (porcentaje.compareTo(BigDecimal.valueOf(umbralMedio)) >= 0) {
            return MEDIO;
        }
        return OK;
    }
}
