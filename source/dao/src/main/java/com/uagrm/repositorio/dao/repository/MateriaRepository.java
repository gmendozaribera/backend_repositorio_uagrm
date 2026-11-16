package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Materia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MateriaRepository extends JpaRepository<Materia, Integer>, JpaSpecificationExecutor<Materia> {

    /** Sigla unica por facultad. En una creacion idExcluido = 0. */
    boolean existsByFacultadIdFacultadAndSiglaAndIdMateriaNot(Integer idFacultad, String sigla, Integer idExcluido);
}
