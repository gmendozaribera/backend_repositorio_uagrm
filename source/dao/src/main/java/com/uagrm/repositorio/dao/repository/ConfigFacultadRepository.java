package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigFacultadRepository extends JpaRepository<ConfigFacultad, Integer> {

    Optional<ConfigFacultad> findByFacultadIdFacultad(Integer idFacultad);
}
