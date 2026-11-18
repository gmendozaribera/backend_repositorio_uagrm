package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.GrupoDocenteId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrupoDocenteRepository extends JpaRepository<GrupoDocente, GrupoDocenteId> {

    /** Docentes de un grupo con usuario y persona en la misma consulta. */
    @EntityGraph(attributePaths = {"usuario", "usuario.persona"})
    List<GrupoDocente> findByIdIdGrupoOrderByUsuarioPersonaApellidosAsc(Integer idGrupo);

    /** Ids de usuario de los docentes asignados al grupo (p. ej. para notificarles un trabajo enviado). */
    @Query("select gd.id.idUsuario from GrupoDocente gd where gd.id.idGrupo = :idGrupo")
    List<Integer> findIdsDocentes(@Param("idGrupo") Integer idGrupo);

    /**
     * Relacion del docente con el grupo en una sola consulta: vacio = no esta asignado; true = es el Administrador
     * de Curso de ese grupo; false = docente asignado sin ese rol.
     */
    @Query("select gd.esAdminCurso from GrupoDocente gd where gd.id.idGrupo = :idGrupo and gd.id.idUsuario = :idUsuario")
    Optional<Boolean> findEsAdminCurso(@Param("idGrupo") Integer idGrupo, @Param("idUsuario") Integer idUsuario);
}
