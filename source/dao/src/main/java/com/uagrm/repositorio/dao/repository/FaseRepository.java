package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Fase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FaseRepository extends JpaRepository<Fase, Integer>, JpaSpecificationExecutor<Fase> {

    /** Nombre unico por facultad (sin distinguir mayusculas). En una creacion idExcluido = 0. */
    boolean existsByFacultadIdFacultadAndNombreIgnoreCaseAndIdFaseNot(Integer idFacultad, String nombre, Integer idExcluido);
}
