package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Facultad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultadRepository extends JpaRepository<Facultad, Integer> {

    Optional<Facultad> findBySigla(String sigla);
}
