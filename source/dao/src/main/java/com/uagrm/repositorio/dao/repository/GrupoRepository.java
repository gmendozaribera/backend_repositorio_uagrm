package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrupoRepository extends JpaRepository<Grupo, Integer>, JpaSpecificationExecutor<Grupo> {

    /** Nombre unico por materia + semestre. En una creacion idExcluido = 0. */
    boolean existsByMateriaIdMateriaAndSemestreIdSemestreAndNombreAndIdGrupoNot(
            Integer idMateria, Integer idSemestre, String nombre, Integer idExcluido);
}
