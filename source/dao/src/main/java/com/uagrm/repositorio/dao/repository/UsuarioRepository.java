package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>, JpaSpecificationExecutor<Usuario> {

    /** Login: trae rol y persona en la misma consulta. */
    @EntityGraph(attributePaths = {"tipoUsuario", "persona"})
    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    /** Asignacion de docentes: valida el rol y devuelve los datos de la persona en una sola consulta. */
    @EntityGraph(attributePaths = {"tipoUsuario", "persona"})
    Optional<Usuario> findConRolByIdUsuario(Integer idUsuario);

    /** Gestion de usuarios: rol, persona y carrera (siglaCarrera) en una sola consulta. */
    @EntityGraph(attributePaths = {"tipoUsuario", "persona", "persona.carrera"})
    Optional<Usuario> findDetalleByIdUsuario(Integer idUsuario);

    /** Coautores de un trabajo: cuentas cuyo registro universitario esta en la lista, con rol y persona. */
    @EntityGraph(attributePaths = {"tipoUsuario", "persona"})
    List<Usuario> findByPersonaRegistroIn(Collection<Integer> registros);

    /** Ids de las cuentas de estas personas (p. ej. los autores de un trabajo, para notificarlos). */
    @Query("select u.idUsuario from Usuario u where u.persona.idPersona in :idsPersona")
    List<Integer> findIdsByPersonas(@Param("idsPersona") Collection<Integer> idsPersona);

    /** Listado paginado de usuarios con rol, persona y carrera en la misma consulta. */
    @Override
    @EntityGraph(attributePaths = {"tipoUsuario", "persona", "persona.carrera"})
    Page<Usuario> findAll(Specification<Usuario> spec, Pageable pageable);
}
