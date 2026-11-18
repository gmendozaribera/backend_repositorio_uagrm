package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.FuentePlagio;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dto.plagio.AnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoAnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoPlagioCache;
import com.uagrm.repositorio.dto.plagio.FuentePlagioResponse;
import java.util.List;
import java.util.Set;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Mapeos de analisis de plagio.
 *
 * <p>Regla del contrato para fuentes INTERNA: solo si el trabajo fuente esta publicado (EVALUADO, su id viene en
 * {@code publicados}) se devuelven su titulo y su id. Si no, titulo generico e idTrabajo null: un docente no debe
 * enterarse por aqui de trabajos ajenos que aun no se publicaron. La regla se aplica al LEER, asi una fuente que
 * se publica despues aparece con su titulo.</p>
 */
@Mapper
public interface PlagioMapper {

    /** Titulo de una fuente interna no publicada (texto fijo del contrato plagio-api.yaml). */
    String TITULO_NO_PUBLICADO = "Trabajo no publicado del repositorio interno";

    @Mapping(target = "idTrabajo", source = "trabajo.idTrabajo")
    @Mapping(target = "porcentajeSimilitud", source = "porcentaje")
    @Mapping(target = "iniciadoPor", source = "usuarioInicio.persona", qualifiedByName = "nombreCompleto")
    AnalisisPlagioResponse toResponse(AnalisisPlagio analisis, @Context Set<Integer> publicados);

    List<FuentePlagioResponse> toFuentes(List<FuentePlagio> fuentes, @Context Set<Integer> publicados);

    default FuentePlagioResponse toFuente(FuentePlagio fuente, @Context Set<Integer> publicados) {
        double porcentaje = fuente.getPorcentaje().doubleValue();
        if (fuente.getTipo() == TipoFuentePlagio.INTERNET) {
            return new FuentePlagioResponse(TipoFuentePlagio.INTERNET, fuente.getTitulo(), fuente.getUrl(), null,
                    porcentaje);
        }
        Integer id = fuente.getIdTrabajoFuente();
        boolean publicado = id != null && publicados.contains(id);
        return new FuentePlagioResponse(TipoFuentePlagio.INTERNA,
                publicado ? fuente.getTitulo() : TITULO_NO_PUBLICADO, null, publicado ? id : null, porcentaje);
    }

    @Mapping(target = "porcentaje", source = "porcentaje")
    EstadoPlagioCache toCache(AnalisisPlagio analisis);

    @Mapping(target = "porcentajeSimilitud", source = "porcentaje")
    EstadoAnalisisPlagioResponse toEstado(EstadoPlagioCache cache);

    @Named("nombreCompleto")
    default String nombreCompleto(Persona persona) {
        return persona == null ? null : persona.getNombre() + " " + persona.getApellidos();
    }
}
