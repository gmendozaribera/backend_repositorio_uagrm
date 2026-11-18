package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrabajoHistorialRepository extends JpaRepository<TrabajoHistorial, Integer> {

    /** Historial del detalle (del mas antiguo al mas reciente) con el nombre de quien hizo cada cambio. */
    @EntityGraph(attributePaths = {"usuario", "usuario.persona"})
    List<TrabajoHistorial> findByTrabajoIdTrabajoOrderByFechaAscIdHistorialAsc(Integer idTrabajo);

    /** Ultima vez que el trabajo paso al estado dado (vacio si nunca paso). Usa idx_trabajo_historial_trabajo. */
    @Query("select max(h.fecha) from TrabajoHistorial h where h.trabajo.idTrabajo = :idTrabajo and h.estadoNuevo = :estado")
    Optional<Instant> findUltimaFechaEstado(@Param("idTrabajo") Integer idTrabajo,
                                            @Param("estado") EstadoTrabajo estado);

    /** Igual que {@link #findUltimaFechaEstado} para varios trabajos en una consulta: filas [idTrabajo, fecha]. */
    @Query("select h.trabajo.idTrabajo, max(h.fecha) from TrabajoHistorial h "
            + "where h.trabajo.idTrabajo in :ids and h.estadoNuevo = :estado group by h.trabajo.idTrabajo")
    List<Object[]> findUltimasFechasEstado(@Param("ids") Collection<Integer> idsTrabajo,
                                           @Param("estado") EstadoTrabajo estado);
}
