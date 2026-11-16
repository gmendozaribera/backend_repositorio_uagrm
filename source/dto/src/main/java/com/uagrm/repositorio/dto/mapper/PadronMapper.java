package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dto.padron.EstudianteHabilitadoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PadronMapper {

    /** registrado no es columna: el service lo calcula (existe una persona con ese registro). */
    @Mapping(target = "idCarrera", source = "estudiante.carrera.idCarrera")
    @Mapping(target = "siglaCarrera", source = "estudiante.carrera.sigla")
    @Mapping(target = "registrado", source = "registrado")
    EstudianteHabilitadoResponse toResponse(EstudianteHabilitado estudiante, Boolean registrado);
}
