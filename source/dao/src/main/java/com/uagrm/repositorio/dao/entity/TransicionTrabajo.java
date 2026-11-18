package com.uagrm.repositorio.dao.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maquina de estados del trabajo: cada transicion declara desde que estados se puede aplicar, a que estado lleva y
 * quien la ejecuta. Es la UNICA fuente de verdad del ciclo de vida (el service y las "acciones" del detalle la
 * consultan); una transicion no listada aqui no existe (409).
 *
 * <pre>
 * BORRADOR | RECHAZADO | APROBADO_CON_OBSERVACIONES --ENVIAR (autor)--> PENDIENTE
 * PENDIENTE --TOMAR_REVISION (docente del grupo)--> EN_REVISION
 * PENDIENTE | EN_REVISION --APROBAR | APROBAR_CON_OBSERVACIONES | RECHAZAR (docente del grupo)--> destino
 * APROBADO --> EVALUADO llega en el Sprint 4.
 * </pre>
 */
public enum TransicionTrabajo {

    ENVIAR(Actor.AUTOR, EstadoTrabajo.PENDIENTE,
            EnumSet.of(EstadoTrabajo.BORRADOR, EstadoTrabajo.RECHAZADO, EstadoTrabajo.APROBADO_CON_OBSERVACIONES)),
    TOMAR_REVISION(Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.EN_REVISION,
            EnumSet.of(EstadoTrabajo.PENDIENTE)),
    APROBAR(Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.APROBADO,
            EnumSet.of(EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION)),
    APROBAR_CON_OBSERVACIONES(Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.APROBADO_CON_OBSERVACIONES,
            EnumSet.of(EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION)),
    RECHAZAR(Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.RECHAZADO,
            EnumSet.of(EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION));

    /** Quien puede ejecutar la transicion (ademas de que el trabajo este en un estado de origen). */
    public enum Actor {
        AUTOR,
        DOCENTE_DEL_GRUPO
    }

    private final Actor actor;
    private final EstadoTrabajo destino;
    private final Set<EstadoTrabajo> origenes;

    TransicionTrabajo(Actor actor, EstadoTrabajo destino, Set<EstadoTrabajo> origenes) {
        this.actor = actor;
        this.destino = destino;
        this.origenes = origenes;
    }

    public Actor actor() {
        return actor;
    }

    public EstadoTrabajo destino() {
        return destino;
    }

    /** true si la transicion se puede aplicar a un trabajo en ese estado. */
    public boolean aplicableDesde(EstadoTrabajo estado) {
        return origenes.contains(estado);
    }

    /** Estados de origen (copia inmutable). */
    public Set<EstadoTrabajo> origenes() {
        return Set.copyOf(origenes);
    }
}
