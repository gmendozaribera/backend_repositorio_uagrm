package com.uagrm.repositorio.dao.entity;

/**
 * Codigo del evento que origina una notificacion (columna {@code notificacion.tipo}, VARCHAR(50)).
 * Nunca strings sueltos: cada disparador usa una de estas constantes. El Sprint 3 agrega las de trabajos
 * (TRABAJO_APROBADO...). No renombrar ni quitar valores ya usados: las filas guardadas dejarian de leerse.
 */
public enum TipoNotificacion {

    /** Cuenta nueva (auto-registro del estudiante o alta por el administrador). */
    BIENVENIDA,
    /** El administrador asigno una contrasena temporal. */
    PASSWORD_RESTABLECIDA
}
