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
    PASSWORD_RESTABLECIDA,

    // ---------- Sprint 3: trabajos ----------

    /** A los docentes del grupo: un trabajo llego (o volvio) a su bandeja de revision. */
    TRABAJO_ENVIADO,
    /** A los autores: un docente tomo su trabajo en revision. */
    TRABAJO_EN_REVISION,
    /** A los autores. */
    TRABAJO_APROBADO,
    /** A los autores: aprobado pero con correcciones pedidas (pueden reenviar). */
    TRABAJO_APROBADO_CON_OBSERVACIONES,
    /** A los autores, con el motivo en el detalle del trabajo. */
    TRABAJO_RECHAZADO,

    // ---------- Sprint 4 ----------

    /** A los autores: el Administrador de Curso califico el trabajo y quedo publicado (la nota NO va en el aviso). */
    TRABAJO_EVALUADO
}
