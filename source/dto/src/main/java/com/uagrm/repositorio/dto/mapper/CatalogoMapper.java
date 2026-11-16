package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Fase;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dto.catalogo.CarreraRequest;
import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.dto.catalogo.DocenteGrupoResponse;
import com.uagrm.repositorio.dto.catalogo.FacultadRequest;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
import com.uagrm.repositorio.dto.catalogo.FaseRequest;
import com.uagrm.repositorio.dto.catalogo.FaseResponse;
import com.uagrm.repositorio.dto.catalogo.GrupoRequest;
import com.uagrm.repositorio.dto.catalogo.GrupoResponse;
import com.uagrm.repositorio.dto.catalogo.MateriaRequest;
import com.uagrm.repositorio.dto.catalogo.MateriaResponse;
import com.uagrm.repositorio.dto.catalogo.SemestreRequest;
import com.uagrm.repositorio.dto.catalogo.SemestreResponse;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoRequest;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapeos de catalogos. Los metodos actualizar(...) copian el request sobre la entidad (nueva o existente):
 * un estado null conserva el valor actual (en una entidad nueva, ACTIVO). Las relaciones (facultad, materia,
 * semestre) no se mapean aqui: el service las resuelve y valida.
 */
@Mapper
public interface CatalogoMapper {

    // ---------- Entidad -> Response ----------

    FacultadResponse toResponse(Facultad facultad);

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    CarreraResponse toResponse(Carrera carrera);

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    MateriaResponse toResponse(Materia materia);

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    SemestreResponse toResponse(Semestre semestre);

    @Mapping(target = "idMateria", source = "materia.idMateria")
    @Mapping(target = "idSemestre", source = "semestre.idSemestre")
    GrupoResponse toResponse(Grupo grupo);

    @Mapping(target = "idGrupo", source = "id.idGrupo")
    @Mapping(target = "idUsuario", source = "id.idUsuario")
    @Mapping(target = "usuario", source = "usuario.usuario")
    @Mapping(target = "nombre", source = "usuario.persona.nombre")
    @Mapping(target = "apellidos", source = "usuario.persona.apellidos")
    DocenteGrupoResponse toResponse(GrupoDocente grupoDocente);

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    FaseResponse toResponse(Fase fase);

    @Mapping(target = "idFacultad", source = "facultad.idFacultad")
    TipoTrabajoResponse toResponse(TipoTrabajo tipoTrabajo);

    // ---------- Request -> Entidad ----------

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idFacultad", ignore = true)
    void actualizar(FacultadRequest request, @MappingTarget Facultad facultad);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idCarrera", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    void actualizar(CarreraRequest request, @MappingTarget Carrera carrera);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idMateria", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    void actualizar(MateriaRequest request, @MappingTarget Materia materia);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idSemestre", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    void actualizar(SemestreRequest request, @MappingTarget Semestre semestre);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idGrupo", ignore = true)
    @Mapping(target = "materia", ignore = true)
    @Mapping(target = "semestre", ignore = true)
    void actualizar(GrupoRequest request, @MappingTarget Grupo grupo);

    /** PUT reemplaza la descripcion: si llega null se borra. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idFase", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    @Mapping(target = "descripcion", source = "descripcion",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void actualizar(FaseRequest request, @MappingTarget Fase fase);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "idTipoTrabajo", ignore = true)
    @Mapping(target = "facultad", ignore = true)
    void actualizar(TipoTrabajoRequest request, @MappingTarget TipoTrabajo tipoTrabajo);
}
