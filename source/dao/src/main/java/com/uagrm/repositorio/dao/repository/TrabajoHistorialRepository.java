package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrabajoHistorialRepository extends JpaRepository<TrabajoHistorial, Integer> {

    /** Historial del detalle (del mas antiguo al mas reciente) con el nombre de quien hizo cada cambio. */
    @EntityGraph(attributePaths = {"usuario", "usuario.persona"})
    List<TrabajoHistorial> findByTrabajoIdTrabajoOrderByFechaAscIdHistorialAsc(Integer idTrabajo);
}
