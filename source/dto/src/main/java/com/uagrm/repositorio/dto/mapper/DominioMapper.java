package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest;
import com.uagrm.repositorio.dto.dominio.CrearDominioRequest;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapeos de la tabla de dominio. sistema y secreto no son columnas: los calcula el service con ConstantesDominio.
 * lastUser y lastTime los pone el service (admin autenticado y hora actual), nunca el request.
 * Un secreto NUNCA sale en la respuesta: domainValue = "" y configurado = tiene valor guardado.
 */
@Mapper
public interface DominioMapper {

    @Mapping(target = "sistema", source = "sistema")
    @Mapping(target = "secreto", source = "secreto")
    @Mapping(target = "domainValue", expression = "java(secreto ? \"\" : dominio.getDomainValue())")
    @Mapping(target = "configurado",
            expression = "java(!secreto || (dominio.getDomainValue() != null && !dominio.getDomainValue().isEmpty()))")
    DominioResponse toResponse(Dominio dominio, boolean sistema, boolean secreto);

    @Mapping(target = "domainId", ignore = true)
    @Mapping(target = "lastUser", ignore = true)
    @Mapping(target = "lastTime", ignore = true)
    Dominio toEntity(CrearDominioRequest request);

    /** domainValue null = conservar el valor actual (no se copia). */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "domainId", ignore = true)
    @Mapping(target = "domainGroup", ignore = true)
    @Mapping(target = "domainName", ignore = true)
    @Mapping(target = "lastUser", ignore = true)
    @Mapping(target = "lastTime", ignore = true)
    void actualizar(ActualizarDominioRequest request, @MappingTarget Dominio dominio);
}
