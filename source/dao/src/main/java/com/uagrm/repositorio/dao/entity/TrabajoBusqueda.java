package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Vista de SOLO LECTURA de la columna {@code trabajo.busqueda} (tsvector, V10) para usarla en la busqueda full-text
 * del repositorio (TrabajoSpecs.busquedaTexto) con un join por id.
 *
 * <p>Por que una entidad aparte sobre la misma tabla: si la columna se mapeara en {@link Trabajo}, cada consulta de
 * trabajos (listados, detalle, bloqueos) leeria el vector completo, que no se usa nunca en Java. Aqui solo aparece
 * dentro del WHERE/ORDER BY de la busqueda y jamas se carga como entidad. La columna la mantiene PostgreSQL con
 * triggers (V10): esta entidad nunca escribe ({@code @Immutable}, insertable/updatable = false).</p>
 *
 * <p>Los nombres de columna salen de la estrategia de nombres (idTrabajo -> id_trabajo), igual que en
 * {@link Trabajo}: Hibernate exige el mismo nombre logico para la misma columna fisica de una tabla.</p>
 */
@Entity
@Immutable
@Table(name = "trabajo")
@Getter
@NoArgsConstructor
public class TrabajoBusqueda {

    @Id
    @Column(insertable = false, updatable = false)
    private Integer idTrabajo;

    /** tsvector; String solo para el validador de esquema (nunca se lee ni se escribe desde Java). */
    @Column(columnDefinition = "tsvector", insertable = false, updatable = false)
    private String busqueda;
}
