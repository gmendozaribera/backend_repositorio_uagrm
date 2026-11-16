package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Facultad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FacultadRepository extends JpaRepository<Facultad, Integer>, JpaSpecificationExecutor<Facultad> {

    Optional<Facultad> findBySigla(String sigla);

    /** Duplicados: en una creacion pasar idExcluido = 0 (los ids empiezan en 1). */
    boolean existsBySiglaAndIdFacultadNot(String sigla, Integer idExcluido);
}
