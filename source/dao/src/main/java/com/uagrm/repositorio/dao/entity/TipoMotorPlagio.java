package com.uagrm.repositorio.dao.entity;

/**
 * Motor que hizo el analisis (columna {@code analisis_plagio.motor}). SIMULADO: sin red, solo para desarrollo;
 * COPYLEAKS: proveedor externo real. Lo elige la variable de entorno PLAGIO_MOTOR de plagiarism-service.
 */
public enum TipoMotorPlagio {
    SIMULADO,
    COPYLEAKS
}
