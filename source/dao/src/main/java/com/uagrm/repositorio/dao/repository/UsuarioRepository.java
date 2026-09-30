package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    /** Login: trae rol y persona en la misma consulta. */
    @EntityGraph(attributePaths = {"tipoUsuario", "persona"})
    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);
}
