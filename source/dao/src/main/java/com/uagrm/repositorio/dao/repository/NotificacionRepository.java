package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Notificacion;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Todas las consultas de la campana filtran por id_usuario (indice id_usuario, leida, fecha_creacion DESC). */
public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    Page<Notificacion> findByIdUsuario(Integer idUsuario, Pageable pageable);

    Page<Notificacion> findByIdUsuarioAndLeidaFalse(Integer idUsuario, Pageable pageable);

    /** Busca SOLO entre las notificaciones del usuario: una ajena se comporta igual que una inexistente. */
    Optional<Notificacion> findByIdNotificacionAndIdUsuario(Integer idNotificacion, Integer idUsuario);

    long countByIdUsuarioAndLeidaFalse(Integer idUsuario);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notificacion n set n.leida = true, n.fechaLectura = :ahora
            where n.idUsuario = :idUsuario and n.leida = false""")
    int marcarTodasLeidas(@Param("idUsuario") Integer idUsuario, @Param("ahora") Instant ahora);

    /** Purga diaria: borra solo las LEIDAS creadas antes del limite (las no leidas nunca se pierden). */
    @Modifying
    @Query("delete from Notificacion n where n.leida = true and n.fechaCreacion < :limite")
    int borrarLeidasAnterioresA(@Param("limite") Instant limite);
}
