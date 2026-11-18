package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.dto.trabajo.AutorResponse;
import com.uagrm.repositorio.dto.trabajo.CambioEstadoResponse;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResumenResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Mapeos de trabajos. Los autores salen con el creador primero y luego por apellidos; los hashtags en orden
 * alfabetico. Documentos, historial y acciones los arma el service (consultas aparte) y llegan como parametros.
 */
@Mapper
public interface TrabajoMapper {

    @Mapping(target = "idTrabajo", source = "trabajo.idTrabajo")
    @Mapping(target = "titulo", source = "trabajo.titulo")
    @Mapping(target = "estado", source = "trabajo.estado")
    @Mapping(target = "idGrupo", source = "trabajo.grupo.idGrupo")
    @Mapping(target = "grupo", source = "trabajo.grupo.nombre")
    @Mapping(target = "idMateria", source = "trabajo.materia.idMateria")
    @Mapping(target = "siglaMateria", source = "trabajo.materia.sigla")
    @Mapping(target = "nombreMateria", source = "trabajo.materia.nombre")
    @Mapping(target = "idSemestre", source = "trabajo.semestre.idSemestre")
    @Mapping(target = "periodo", source = "trabajo.semestre", qualifiedByName = "periodo")
    @Mapping(target = "idFacultad", source = "trabajo.materia.facultad.idFacultad")
    @Mapping(target = "idTipoTrabajo", source = "trabajo.tipoTrabajo.idTipoTrabajo")
    @Mapping(target = "tipoTrabajo", source = "trabajo.tipoTrabajo.nombre")
    @Mapping(target = "autores", source = "trabajo", qualifiedByName = "autores")
    @Mapping(target = "hashtags", source = "trabajo.hashtags", qualifiedByName = "hashtags")
    @Mapping(target = "documentos", source = "documentos")
    @Mapping(target = "historial", source = "historial")
    @Mapping(target = "acciones", source = "acciones")
    @Mapping(target = "resumen", source = "trabajo.resumen")
    @Mapping(target = "observaciones", source = "trabajo.observaciones")
    @Mapping(target = "fechaCreacion", source = "trabajo.fechaCreacion")
    @Mapping(target = "fechaActualizacion", source = "trabajo.fechaActualizacion")
    @Mapping(target = "fechaEnvio", source = "trabajo.fechaEnvio")
    TrabajoResponse toResponse(Trabajo trabajo, List<Documento> documentos, List<TrabajoHistorial> historial,
                               AccionesResponse acciones, @Context Integer idPersonaActual);

    @Mapping(target = "idGrupo", source = "grupo.idGrupo")
    @Mapping(target = "grupo", source = "grupo.nombre")
    @Mapping(target = "idMateria", source = "materia.idMateria")
    @Mapping(target = "siglaMateria", source = "materia.sigla")
    @Mapping(target = "nombreMateria", source = "materia.nombre")
    @Mapping(target = "idSemestre", source = "semestre.idSemestre")
    @Mapping(target = "periodo", source = "semestre", qualifiedByName = "periodo")
    @Mapping(target = "tipoTrabajo", source = "tipoTrabajo.nombre")
    @Mapping(target = "autores", source = ".", qualifiedByName = "nombresAutores")
    TrabajoResumenResponse toResumen(Trabajo trabajo);

    @Mapping(target = "idFase", source = "fase.idFase")
    @Mapping(target = "nombreFase", source = "fase.nombre")
    DocumentoResponse toResponse(Documento documento);

    @Mapping(target = "usuario", source = "usuario.persona", qualifiedByName = "nombreCompleto")
    CambioEstadoResponse toResponse(TrabajoHistorial historial);

    /** esUsuarioActual: el frontend lo usa para no ofrecer quitarse a si mismo de los coautores. */
    default AutorResponse toAutor(Persona persona, Integer idPersonaActual) {
        return new AutorResponse(persona.getIdPersona(), persona.getNombre(), persona.getApellidos(),
                persona.getRegistro(), Objects.equals(persona.getIdPersona(), idPersonaActual));
    }

    /** "2-2026": numero de periodo y anio. */
    @Named("periodo")
    default String periodo(Semestre semestre) {
        return semestre == null ? null : semestre.getSemestre() + "-" + semestre.getAnio();
    }

    @Named("nombreCompleto")
    default String nombreCompleto(Persona persona) {
        return persona == null ? null : persona.getNombre() + " " + persona.getApellidos();
    }

    @Named("autores")
    default List<AutorResponse> autores(Trabajo trabajo, @Context Integer idPersonaActual) {
        return ordenados(trabajo).stream().map(p -> toAutor(p, idPersonaActual)).toList();
    }

    @Named("nombresAutores")
    default List<String> nombresAutores(Trabajo trabajo) {
        return ordenados(trabajo).stream().map(this::nombreCompleto).toList();
    }

    @Named("hashtags")
    default List<String> hashtags(Set<Hashtag> hashtags) {
        return hashtags.stream().map(Hashtag::getTexto).sorted().toList();
    }

    /** Creador primero; el resto por apellidos y nombre. */
    private static List<Persona> ordenados(Trabajo trabajo) {
        Integer idCreador = trabajo.getCreador() == null ? null : trabajo.getCreador().getIdPersona();
        return trabajo.getAutores().stream()
                .sorted(Comparator.comparing((Persona p) -> !Objects.equals(p.getIdPersona(), idCreador))
                        .thenComparing(Persona::getApellidos)
                        .thenComparing(Persona::getNombre))
                .toList();
    }
}
