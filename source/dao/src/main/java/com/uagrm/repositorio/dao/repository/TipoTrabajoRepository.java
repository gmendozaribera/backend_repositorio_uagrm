package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TipoTrabajoRepository extends JpaRepository<TipoTrabajo, Integer>, JpaSpecificationExecutor<TipoTrabajo> {

    /** Nombre unico por facultad (sin distinguir mayusculas). En una creacion idExcluido = 0. */
    boolean existsByFacultadIdFacultadAndNombreIgnoreCaseAndIdTipoTrabajoNot(
            Integer idFacultad, String nombre, Integer idExcluido);
}
