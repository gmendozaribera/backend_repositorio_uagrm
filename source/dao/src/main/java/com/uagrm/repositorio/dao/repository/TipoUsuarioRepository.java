package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoUsuarioRepository extends JpaRepository<TipoUsuario, Integer> {

    Optional<TipoUsuario> findByNombre(String nombre);
}
