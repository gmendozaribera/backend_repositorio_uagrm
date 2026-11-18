package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigFacultadRepository extends JpaRepository<ConfigFacultad, Integer> {

    Optional<ConfigFacultad> findByFacultadIdFacultad(Integer idFacultad);

    /**
     * Ids de las facultades con acceso externo (anonimo) habilitado, en una sola consulta: las que tienen fila en
     * config_facultad con acceso_externo = true y, si el valor por defecto (FACULTAD_DEFAULT_GROUP.ACCESO_EXTERNO)
     * es true, tambien las que no tienen fila propia. Es la misma regla que ConfigFacultadService.configDe.
     */
    @Query("""
            select f.idFacultad from Facultad f left join ConfigFacultad c on c.facultad = f
            where c.accesoExterno = true or (c.id is null and :porDefecto = true)
            order by f.idFacultad""")
    List<Integer> findIdsFacultadesConAccesoExterno(@Param("porDefecto") boolean porDefecto);
}
