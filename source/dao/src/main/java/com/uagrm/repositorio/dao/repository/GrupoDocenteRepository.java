package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.GrupoDocenteId;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoDocenteRepository extends JpaRepository<GrupoDocente, GrupoDocenteId> {

    /** Docentes de un grupo con usuario y persona en la misma consulta. */
    @EntityGraph(attributePaths = {"usuario", "usuario.persona"})
    List<GrupoDocente> findByIdIdGrupoOrderByUsuarioPersonaApellidosAsc(Integer idGrupo);
}
