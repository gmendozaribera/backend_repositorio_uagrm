package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EstudianteHabilitadoRepository
        extends JpaRepository<EstudianteHabilitado, Integer>, JpaSpecificationExecutor<EstudianteHabilitado> {

    /** Auto-registro: el estudiante envia su CI y su registro; ambos deben coincidir en la misma fila. */
    @EntityGraph(attributePaths = "carrera")
    Optional<EstudianteHabilitado> findByCiAndRegistro(Integer ci, Integer registro);

    /** Listado paginado: trae la carrera en la misma consulta (siglaCarrera). */
    @Override
    @EntityGraph(attributePaths = "carrera")
    Page<EstudianteHabilitado> findAll(Specification<EstudianteHabilitado> spec, Pageable pageable);

    @EntityGraph(attributePaths = "carrera")
    Optional<EstudianteHabilitado> findConCarreraById(Integer id);

    /** Importacion: filas existentes para decidir insertar o actualizar (upsert por registro). */
    List<EstudianteHabilitado> findByRegistroIn(Collection<Integer> registros);

    /** Importacion: un CI no puede pertenecer a dos registros distintos. */
    List<EstudianteHabilitado> findByCiIn(Collection<Integer> cis);
}
