package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.specification.TrabajoSpecs;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.TrabajoMapper;
import com.uagrm.repositorio.dto.trabajo.EstadoAccesoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositorio: consulta de trabajos EVALUADO por cualquier usuario autenticado y, sin sesion, de las facultades
 * con acceso externo ({@link AccesoExternoService}). Nunca expone calificacion, observaciones ni historial.
 *
 * <p><b>Busqueda full-text</b> (V10, {@link TrabajoSpecs#busquedaTexto}): espanol con raices y sin tildes sobre
 * titulo (peso A), hashtags (B) y resumen (C); admite "frase", -excluir y OR. Con {@code q}: orden por relevancia
 * ({@code ts_rank_cd}) y luego por fecha de publicacion. Sin {@code q}: mas recientes primero.
 * Decision: si {@code q} solo tiene stopwords ("de", "la y el") la consulta full-text queda vacia y la busqueda
 * devuelve 0 resultados (comportamiento nativo de PostgreSQL; no se ignora el texto en silencio, asi el usuario ve
 * que su busqueda no tenia palabras utiles). {@code q} en blanco = sin filtro de texto.</p>
 *
 * <p>Orden "mas recientes primero" = {@code fecha_actualizacion DESC}: EVALUADO es un estado final y no editable,
 * asi que su fecha de actualizacion es la fecha en que se publico. La ficha y cada tarjeta muestran la fecha exacta
 * de la transicion del historial (fallback: fecha_actualizacion).</p>
 *
 * <p>Sin N+1: grupo, materia, semestre y tipo van en la misma consulta (EntityGraph de findAll), el vector de
 * busqueda en un join 1 a 1 de esa misma consulta; autores y hashtags de toda la pagina se cargan en lote
 * (@BatchSize en Trabajo) y las fechas de publicacion en una consulta agrupada. Una pagina cuesta como maximo:
 * pagina + count + autores + hashtags + fechas (+ la lista de facultades habilitadas en el acceso anonimo).</p>
 */
@Service
@RequiredArgsConstructor
public class RepositorioService {

    private static final Sort ORDEN_PUBLICACION = Sort.by(Sort.Direction.DESC, "fechaActualizacion", "idTrabajo");

    private final TrabajoRepository trabajoRepository;
    private final DocumentoRepository documentoRepository;
    private final TrabajoHistorialRepository historialRepository;
    private final AccesoTrabajos acceso;
    private final AccesoExternoService accesoExterno;
    private final TrabajoMapper mapper;

    // ---------- Usuarios autenticados ----------

    @Transactional(readOnly = true)
    public PaginaResponse<TrabajoRepositorioResumenResponse> buscar(FiltrosRepositorio filtros, int pagina,
                                                                    int tamano) {
        return buscar(especificacion(filtros), filtros, pagina, tamano);
    }

    /** Ficha publica; 404 (mismo mensaje que un id inexistente) si no existe o no esta EVALUADO. */
    @Transactional(readOnly = true)
    public TrabajoPublicoResponse obtener(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        return ficha(evaluado(idTrabajo), actor.idPersona());
    }

    // ---------- Acceso anonimo (sin token) ----------

    /**
     * Igual que {@link #buscar(FiltrosRepositorio, int, int)} pero solo con trabajos de facultades con acceso
     * externo. Si ninguna lo tiene: 403 (el repositorio publico esta cerrado). Un filtro idFacultad de una facultad
     * cerrada simplemente no da resultados.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<TrabajoRepositorioResumenResponse> buscarPublico(FiltrosRepositorio filtros, int pagina,
                                                                           int tamano) {
        List<Integer> habilitadas = accesoExterno.facultadesHabilitadas();
        if (habilitadas.isEmpty()) {
            throw cerrado();
        }
        return buscar(especificacion(filtros).and(TrabajoSpecs.facultadEn(habilitadas)), filtros, pagina, tamano);
    }

    /**
     * Ficha sin sesion: 404 si no existe, no esta EVALUADO o su facultad no permite el acceso externo (mismo
     * mensaje en los tres casos: no se revela que el trabajo existe). Ningun autor es "el usuario actual".
     */
    @Transactional(readOnly = true)
    public TrabajoPublicoResponse obtenerPublico(Integer idTrabajo) {
        Trabajo trabajo = evaluado(idTrabajo);
        if (!accesoExterno.habilitada(trabajo.getMateria().getFacultad().getIdFacultad())) {
            throw AccesoTrabajos.noExiste(idTrabajo);
        }
        return ficha(trabajo, null);
    }

    /** Si existe acceso anonimo (al menos una facultad) y cuales facultades lo tienen. */
    @Transactional(readOnly = true)
    public EstadoAccesoPublicoResponse estadoPublico() {
        List<Integer> habilitadas = accesoExterno.facultadesHabilitadas();
        return new EstadoAccesoPublicoResponse(!habilitadas.isEmpty(), habilitadas);
    }

    // ---------- privados ----------

    private static Specification<Trabajo> especificacion(FiltrosRepositorio filtros) {
        return TrabajoSpecs.estado(EstadoTrabajo.EVALUADO)
                .and(TrabajoSpecs.busquedaTexto(filtros.q()))
                .and(TrabajoSpecs.conHashtag(filtros.hashtag()))
                .and(TrabajoSpecs.grupo(filtros.idGrupo()))
                .and(TrabajoSpecs.facultad(filtros.idFacultad()))
                .and(TrabajoSpecs.carreraDeAlgunAutor(filtros.idCarrera()))
                .and(TrabajoSpecs.materia(filtros.idMateria()))
                .and(TrabajoSpecs.semestre(filtros.idSemestre()))
                .and(TrabajoSpecs.tipoTrabajo(filtros.idTipoTrabajo()));
    }

    /** Con texto el orden (relevancia) lo pone la Specification: el Pageable va sin Sort para no pisarlo. */
    private PaginaResponse<TrabajoRepositorioResumenResponse> buscar(Specification<Trabajo> spec,
                                                                     FiltrosRepositorio filtros, int pagina,
                                                                     int tamano) {
        boolean conTexto = filtros.q() != null && !filtros.q().isBlank();
        PageRequest pageable = conTexto ? PageRequest.of(pagina, tamano)
                : PageRequest.of(pagina, tamano, ORDEN_PUBLICACION);
        Page<Trabajo> page = trabajoRepository.findAll(spec, pageable);
        Map<Integer, Instant> fechas = fechasPublicacion(page.getContent());
        return PaginaResponse.de(page, t -> mapper.toRepositorioResumen(t,
                fechas.getOrDefault(t.getIdTrabajo(), t.getFechaActualizacion())));
    }

    /** Fecha de la transicion a EVALUADO de todos los trabajos de la pagina, en una sola consulta. */
    private Map<Integer, Instant> fechasPublicacion(List<Trabajo> trabajos) {
        if (trabajos.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = trabajos.stream().map(Trabajo::getIdTrabajo).toList();
        Map<Integer, Instant> fechas = new HashMap<>();
        for (Object[] fila : historialRepository.findUltimasFechasEstado(ids, EstadoTrabajo.EVALUADO)) {
            fechas.put((Integer) fila[0], (Instant) fila[1]);
        }
        return fechas;
    }

    private Trabajo evaluado(Integer idTrabajo) {
        return trabajoRepository.findDetalleByIdTrabajo(idTrabajo)
                .filter(t -> t.getEstado() == EstadoTrabajo.EVALUADO)
                .orElseThrow(() -> AccesoTrabajos.noExiste(idTrabajo));
    }

    private TrabajoPublicoResponse ficha(Trabajo trabajo, Integer idPersonaActual) {
        Integer idTrabajo = trabajo.getIdTrabajo();
        Instant fechaPublicacion = historialRepository.findUltimaFechaEstado(idTrabajo, EstadoTrabajo.EVALUADO)
                .orElse(trabajo.getFechaActualizacion());
        return mapper.toPublico(trabajo,
                documentoRepository.findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(idTrabajo),
                fechaPublicacion, idPersonaActual);
    }

    private static NegocioException cerrado() {
        return NegocioException.accesoDenegado(
                "El repositorio publico no esta habilitado: inicie sesion para consultar los trabajos.");
    }
}
