package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Carrera;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CarreraRepository extends JpaRepository<Carrera, Integer>, JpaSpecificationExecutor<Carrera> {

    Optional<Carrera> findBySigla(String sigla);

    /** La sigla de carrera es unica en todo el sistema (V1). En una creacion idExcluido = 0. */
    boolean existsBySiglaAndIdCarreraNot(String sigla, Integer idExcluido);
}
