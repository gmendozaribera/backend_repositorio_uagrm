package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Semestre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SemestreRepository extends JpaRepository<Semestre, Integer>, JpaSpecificationExecutor<Semestre> {

    /** Facultad + anio + semestre unicos. En una creacion idExcluido = 0. */
    boolean existsByFacultadIdFacultadAndAnioAndSemestreAndIdSemestreNot(
            Integer idFacultad, Integer anio, Integer semestre, Integer idExcluido);
}
