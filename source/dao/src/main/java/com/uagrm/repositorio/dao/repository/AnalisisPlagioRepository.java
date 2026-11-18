package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalisisPlagioRepository extends JpaRepository<AnalisisPlagio, Integer> {

    boolean existsByTrabajoIdTrabajoAndEstado(Integer idTrabajo, EstadoAnalisisPlagio estado);

    /** Ultimo analisis del trabajo con quien lo inicio (documentos y fuentes por lote). */
    @EntityGraph(attributePaths = {"usuarioInicio", "usuarioInicio.persona"})
    Optional<AnalisisPlagio> findFirstByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(Integer idTrabajo);

    /** Historial del trabajo, del mas reciente al mas antiguo. */
    @EntityGraph(attributePaths = {"usuarioInicio", "usuarioInicio.persona"})
    List<AnalisisPlagio> findByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(Integer idTrabajo);

    /**
     * Analisis bloqueado (SELECT ... FOR UPDATE) hasta el commit: dos avisos del motor sobre el mismo analisis
     * (p. ej. dos documentos que terminan a la vez, o el timeout y el resultado) se procesan uno tras otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalisisPlagio a where a.idAnalisis = :id")
    Optional<AnalisisPlagio> findParaActualizar(@Param("id") Integer idAnalisis);

    @Query("select a.idAnalisis from AnalisisPlagio a where a.scanId = :scanId")
    Optional<Integer> findIdByScanId(@Param("scanId") String scanId);

    /** Id del trabajo analizado (para resolver fuentes internas que apuntan a otro analisis). */
    @Query("select a.trabajo.idTrabajo from AnalisisPlagio a where a.scanId = :scanId")
    Optional<Integer> findIdTrabajoByScanId(@Param("scanId") String scanId);

    /** Analisis en curso iniciados antes del limite (job de timeout). */
    @Query("select a.idAnalisis from AnalisisPlagio a where a.estado = :estado and a.fechaInicio < :limite "
            + "order by a.fechaInicio")
    List<Integer> findIdsPorEstadoIniciadosAntesDe(@Param("estado") EstadoAnalisisPlagio estado,
                                                   @Param("limite") Instant limite);

    @Query("select a.idAnalisis from AnalisisPlagio a where a.estado = :estado and a.motor = :motor "
            + "order by a.fechaInicio")
    List<Integer> findIdsPorEstadoYMotor(@Param("estado") EstadoAnalisisPlagio estado,
                                         @Param("motor") TipoMotorPlagio motor);
}
