package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Carrera;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarreraRepository extends JpaRepository<Carrera, Integer> {

    Optional<Carrera> findBySigla(String sigla);
}
