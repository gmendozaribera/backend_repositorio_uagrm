package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.auth.UsuarioSesionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UsuarioMapper {

    @Mapping(target = "nombre", source = "persona.nombre")
    @Mapping(target = "apellidos", source = "persona.apellidos")
    @Mapping(target = "correo", source = "persona.correo")
    @Mapping(target = "rol", source = "tipoUsuario.nombre")
    UsuarioSesionDto toSesion(Usuario usuario);
}
