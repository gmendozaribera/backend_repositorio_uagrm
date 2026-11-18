package com.uagrm.repositorio.dao.entity;

/**
 * Estado del ciclo de vida de un trabajo (columna {@code trabajo.estado}, VARCHAR(30) con CHECK en V8).
 * Las transiciones permitidas estan en {@link TransicionTrabajo} (maquina de estados explicita).
 * No renombrar valores: las filas guardadas y el contrato work-api.yaml usan estos nombres.
 */
public enum EstadoTrabajo {

    BORRADOR(true),
    PENDIENTE(false),
    EN_REVISION(false),
    APROBADO(false),
    /** El docente aprobo pero pidio correcciones: el estudiante puede corregir y reenviar. */
    APROBADO_CON_OBSERVACIONES(true),
    RECHAZADO(true),
    /** Sprint 4 (calificacion). */
    EVALUADO(false);

    private final boolean editable;

    EstadoTrabajo(boolean editable) {
        this.editable = editable;
    }

    /** true si los autores pueden editar datos y subir/quitar documentos en este estado. */
    public boolean editable() {
        return editable;
    }
}
