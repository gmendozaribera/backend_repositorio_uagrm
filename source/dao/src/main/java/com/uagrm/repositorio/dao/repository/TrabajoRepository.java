package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Trabajo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TrabajoRepository extends JpaRepository<Trabajo, Integer>, JpaSpecificationExecutor<Trabajo> {

    /** Detalle: grupo, materia (con facultad), semestre y tipo en la misma consulta (autores/hashtags por lote). */
    @EntityGraph(attributePaths = {"grupo", "materia", "materia.facultad", "semestre", "tipoTrabajo"})
    Optional<Trabajo> findDetalleByIdTrabajo(Integer idTrabajo);

    /**
     * Igual que {@link #findDetalleByIdTrabajo} pero bloqueando la fila (SELECT ... FOR UPDATE) hasta el commit:
     * dos cambios de estado simultaneos sobre el mismo trabajo se ejecutan uno despues del otro y el segundo ve
     * el estado ya cambiado (409 si su transicion dejo de ser valida).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trabajo t where t.idTrabajo = :id")
    Optional<Trabajo> findParaActualizar(@Param("id") Integer idTrabajo);

    /** Listados paginados: las relaciones *-a-uno en la misma consulta (las colecciones van por @BatchSize). */
    @Override
    @EntityGraph(attributePaths = {"grupo", "materia", "semestre", "tipoTrabajo"})
    Page<Trabajo> findAll(Specification<Trabajo> spec, Pageable pageable);

    /** De los ids dados, los que estan en el estado indicado (plagio: que fuentes internas estan publicadas). */
    @Query("select t.idTrabajo from Trabajo t where t.idTrabajo in :ids and t.estado = :estado")
    Set<Integer> findIdsEnEstado(@Param("ids") Collection<Integer> ids, @Param("estado") EstadoTrabajo estado);

    /**
     * Trabajos de la facultad que no estan en el estado excluido (BORRADOR), salvo uno, por id: candidatos a fuente
     * interna del motor SIMULADO.
     */
    @Query("select t from Trabajo t where t.materia.facultad.idFacultad = :idFacultad and t.estado <> :excluido "
            + "and t.idTrabajo <> :idTrabajo order by t.idTrabajo")
    List<Trabajo> findOtrosDeFacultad(@Param("idFacultad") Integer idFacultad, @Param("idTrabajo") Integer idTrabajo,
                                      @Param("excluido") EstadoTrabajo excluido, Pageable pageable);

    /** true si la persona es autora (o coautora) del trabajo. */
    @Query("select count(t) > 0 from Trabajo t join t.autores a where t.idTrabajo = :idTrabajo and a.idPersona = :idPersona")
    boolean esAutor(@Param("idTrabajo") Integer idTrabajo, @Param("idPersona") Integer idPersona);
}
