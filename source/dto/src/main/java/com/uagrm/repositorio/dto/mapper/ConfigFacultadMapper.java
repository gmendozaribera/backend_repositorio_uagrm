package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadRequest;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Mapeos de config_facultad. Una fila guardada siempre es una configuracion personalizada. */
@Mapper
public interface ConfigFacultadMapper {

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    @Mapping(target = "personalizada", constant = "true")
    ConfigFacultadResponse toResponse(ConfigFacultad config);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    void actualizar(ConfigFacultadRequest request, @MappingTarget ConfigFacultad config);
}
