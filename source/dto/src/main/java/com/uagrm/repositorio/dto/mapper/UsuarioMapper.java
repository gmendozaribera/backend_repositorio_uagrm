package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.auth.UsuarioSesionDto;
import com.uagrm.repositorio.dto.usuario.ActualizarUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.CrearUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.TipoUsuarioResponse;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapeos de usuarios. La contrasena, el rol, la carrera y el login no se copian desde los requests:
 * el service los resuelve (hash BCrypt, validacion de rol y carrera, login inmutable).
 */
@Mapper
public interface UsuarioMapper {

    @Mapping(target = "nombre", source = "persona.nombre")
    @Mapping(target = "apellidos", source = "persona.apellidos")
    @Mapping(target = "correo", source = "persona.correo")
    @Mapping(target = "rol", source = "tipoUsuario.nombre")
    UsuarioSesionDto toSesion(Usuario usuario);

    @Mapping(target = "rol", source = "tipoUsuario.nombre")
    @Mapping(target = "idPersona", source = "persona.idPersona")
    @Mapping(target = "nombre", source = "persona.nombre")
    @Mapping(target = "apellidos", source = "persona.apellidos")
    @Mapping(target = "ci", source = "persona.ci")
    @Mapping(target = "correo", source = "persona.correo")
    @Mapping(target = "celular", source = "persona.celular")
    @Mapping(target = "fechaNac", source = "persona.fechaNac")
    @Mapping(target = "registro", source = "persona.registro")
    @Mapping(target = "idCarrera", source = "persona.carrera.idCarrera")
    @Mapping(target = "siglaCarrera", source = "persona.carrera.sigla")
    UsuarioResponse toResponse(Usuario usuario);

    TipoUsuarioResponse toResponse(TipoUsuario tipoUsuario);

    // ---------- Request -> Persona ----------

    @Mapping(target = "idPersona", ignore = true)
    @Mapping(target = "carrera", ignore = true)
    void actualizar(CrearUsuarioRequest request, @MappingTarget Persona persona);

    /**
     * PUT: celular y fechaNac null se borran (SET_TO_NULL por defecto); registro null CONSERVA el actual
     * (un estudiante nunca queda sin registro). La carrera la resuelve el service.
     */
    @Mapping(target = "idPersona", ignore = true)
    @Mapping(target = "carrera", ignore = true)
    @Mapping(target = "registro", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void actualizar(ActualizarUsuarioRequest request, @MappingTarget Persona persona);
}
