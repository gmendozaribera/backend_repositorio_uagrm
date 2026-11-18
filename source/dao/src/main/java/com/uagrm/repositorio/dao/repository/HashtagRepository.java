package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Hashtag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HashtagRepository extends JpaRepository<Hashtag, Integer> {

    List<Hashtag> findByTextoIn(Collection<String> textos);

    /**
     * Crea el hashtag si no existe. ON CONFLICT DO NOTHING: si dos trabajos crean el mismo hashtag a la vez, ninguno
     * falla por la restriccion UNIQUE (ambos reutilizan la misma fila al volver a consultarla).
     */
    @Modifying
    @Query(value = "INSERT INTO hashtag (texto) VALUES (:texto) ON CONFLICT (texto) DO NOTHING", nativeQuery = true)
    int crearSiNoExiste(@Param("texto") String texto);
}
