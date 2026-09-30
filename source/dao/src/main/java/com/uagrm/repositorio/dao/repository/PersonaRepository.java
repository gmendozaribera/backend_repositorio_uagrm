package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Persona;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Integer> {

    Optional<Persona> findByCi(Integer ci);

    Optional<Persona> findByCorreo(String correo);
}
