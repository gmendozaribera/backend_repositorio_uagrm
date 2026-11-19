package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Fila con un secreto TOTP aun en claro (anterior al cifrado de S5). */
    interface SecretoTotpEnClaro {
        Integer getIdUsuario();

        String getTotpSecret();
    }

    /**
     * Migracion de S5: secretos TOTP sin el prefijo {@code v1:} (en claro), por lotes ordenados por id (keyset:
     * {@code desde} = ultimo id del lote anterior, asi un lote que no se pudo actualizar no se repite para siempre).
     */
    @Query("select u.idUsuario as idUsuario, u.totpSecret as totpSecret from Usuario u where u.totpSecret is not null"
            + " and u.totpSecret <> '' and u.totpSecret not like 'v1:%' and u.idUsuario > :desde order by u.idUsuario")
    List<SecretoTotpEnClaro> findTotpEnClaro(@Param("desde") Integer desde, Limit limite);

    /** Reemplaza el secreto SOLO si sigue siendo el mismo valor en claro leido (idempotente ante otra instancia). */
    @Modifying
    @Query("update Usuario u set u.totpSecret = :cifrado where u.idUsuario = :id and u.totpSecret = :enClaro")
    int cifrarTotp(@Param("id") Integer idUsuario, @Param("enClaro") String enClaro, @Param("cifrado") String cifrado);
}
