package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Persona;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PersonaRepository extends JpaRepository<Persona, Integer> {

    Optional<Persona> findByCi(String ci);

    Optional<Persona> findByCorreo(String correo);

    boolean existsByCi(String ci);

    boolean existsByRegistro(Integer registro);

    boolean existsByCorreoIgnoreCase(String correo);

    // Duplicados "que no sea la misma persona": en una creacion idExcluido = 0.

    boolean existsByCiAndIdPersonaNot(String ci, Integer idExcluido);

    boolean existsByRegistroAndIdPersonaNot(Integer registro, Integer idExcluido);

    boolean existsByCorreoIgnoreCaseAndIdPersonaNot(String correo, Integer idExcluido);

    /** Padron: cuales de estos registros ya tienen persona (= el estudiante ya creo su cuenta). */
    @Query("select p.registro from Persona p where p.registro in :registros")
    List<Integer> findRegistrosExistentes(Collection<Integer> registros);

    /** Importacion del padron: personas con estos CI (un CI no puede pertenecer a otro registro). */
    List<Persona> findByCiIn(Collection<String> cis);
}
