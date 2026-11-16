package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest;
import com.uagrm.repositorio.dto.dominio.CrearDominioRequest;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapeos de la tabla de dominio. sistema no es columna: lo calcula el service con ConstantesDominio.
 * lastUser y lastTime los pone el service (admin autenticado y hora actual), nunca el request.
 */
@Mapper
public interface DominioMapper {

    @Mapping(target = "sistema", source = "sistema")
    DominioResponse toResponse(Dominio dominio, boolean sistema);

    @Mapping(target = "domainId", ignore = true)
    @Mapping(target = "lastUser", ignore = true)
    @Mapping(target = "lastTime", ignore = true)
    Dominio toEntity(CrearDominioRequest request);

    @Mapping(target = "domainId", ignore = true)
    @Mapping(target = "domainGroup", ignore = true)
    @Mapping(target = "domainName", ignore = true)
    @Mapping(target = "lastUser", ignore = true)
    @Mapping(target = "lastTime", ignore = true)
    void actualizar(ActualizarDominioRequest request, @MappingTarget Dominio dominio);
}
