package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.dto.trabajo.AutorResponse;
import com.uagrm.repositorio.dto.trabajo.CambioEstadoResponse;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResumenResponse;
import java.time.Instant;
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

    /** Largo maximo (en caracteres, antes de "...") del extracto de las tarjetas del repositorio. */
    int LARGO_EXTRACTO = 200;

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
    @Mapping(target = "calificacion", source = "trabajo", qualifiedByName = "calificacion")
    @Mapping(target = "escalaCalifMax", source = "escalaCalifMax")
    @Mapping(target = "fechaCreacion", source = "trabajo.fechaCreacion")
    @Mapping(target = "fechaActualizacion", source = "trabajo.fechaActualizacion")
    @Mapping(target = "fechaEnvio", source = "trabajo.fechaEnvio")
    TrabajoResponse toResponse(Trabajo trabajo, List<Documento> documentos, List<TrabajoHistorial> historial,
                               AccionesResponse acciones, Integer escalaCalifMax, @Context Integer idPersonaActual);

    /** La calificacion solo se muestra en EVALUADO (defensa extra: en otro estado la columna deberia ser null). */
    @Named("calificacion")
    default Integer calificacion(Trabajo trabajo) {
        return trabajo.getEstado() == EstadoTrabajo.EVALUADO ? trabajo.getCalificacion() : null;
    }

    /** Ficha publica del repositorio: sin calificacion, observaciones, historial ni acciones. */
    @Mapping(target = "idTrabajo", source = "trabajo.idTrabajo")
    @Mapping(target = "titulo", source = "trabajo.titulo")
    @Mapping(target = "resumen", source = "trabajo.resumen")
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
    @Mapping(target = "fechaPublicacion", source = "fechaPublicacion")
    TrabajoPublicoResponse toPublico(Trabajo trabajo, List<Documento> documentos, Instant fechaPublicacion,
                                     @Context Integer idPersonaActual);

    /** Tarjeta de un resultado del repositorio. */
    @Mapping(target = "idTrabajo", source = "trabajo.idTrabajo")
    @Mapping(target = "titulo", source = "trabajo.titulo")
    @Mapping(target = "extracto", source = "trabajo.resumen", qualifiedByName = "extracto")
    @Mapping(target = "hashtags", source = "trabajo.hashtags", qualifiedByName = "hashtags")
    @Mapping(target = "autores", source = "trabajo", qualifiedByName = "nombresAutores")
    @Mapping(target = "siglaMateria", source = "trabajo.materia.sigla")
    @Mapping(target = "nombreMateria", source = "trabajo.materia.nombre")
    @Mapping(target = "periodo", source = "trabajo.semestre", qualifiedByName = "periodo")
    @Mapping(target = "tipoTrabajo", source = "trabajo.tipoTrabajo.nombre")
    @Mapping(target = "fechaPublicacion", source = "fechaPublicacion")
    TrabajoRepositorioResumenResponse toRepositorioResumen(Trabajo trabajo, Instant fechaPublicacion);

    /**
     * Extracto para las tarjetas: espacios y saltos de linea colapsados; si pasa de {@link #LARGO_EXTRACTO}
     * caracteres se corta en el ultimo espacio dentro del limite (o en el limite si no hay ninguno razonable),
     * sin puntuacion colgando, y se agrega "...".
     */
    @Named("extracto")
    default String extracto(String resumen) {
        if (resumen == null) {
            return null;
        }
        String texto = resumen.strip().replaceAll("\\s+", " ");
        if (texto.length() <= LARGO_EXTRACTO) {
            return texto;
        }
        int corte = texto.lastIndexOf(' ', LARGO_EXTRACTO);
        if (corte < LARGO_EXTRACTO / 2) {  // una "palabra" enorme (p. ej. una URL): se corta en el limite
            corte = Character.isHighSurrogate(texto.charAt(LARGO_EXTRACTO - 1)) ? LARGO_EXTRACTO - 1 : LARGO_EXTRACTO;
        }
        String cortado = texto.substring(0, corte).replaceAll("[\\s.,;:\\-]+$", "");
        return cortado + "...";
    }

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
