package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.specification.TrabajoSpecs;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.TrabajoMapper;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
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
 * Repositorio (CU11 basico): consulta de trabajos EVALUADO por cualquier usuario autenticado.
 *
 * <p>Orden "mas recientes primero" = {@code fecha_actualizacion DESC}: EVALUADO es un estado final y no editable,
 * asi que su fecha de actualizacion es la fecha en que se publico (paso a EVALUADO). Ordenar por esa columna evita
 * unir con el historial en cada busqueda y la sirve el indice parcial idx_trabajo_evaluado_fecha (V9). La ficha si
 * y cada tarjeta muestran la fecha exacta de la transicion del historial (fallback: fecha_actualizacion).</p>
 *
 * <p>Sin N+1: grupo, materia, semestre y tipo van en la misma consulta (EntityGraph de findAll); autores y hashtags
 * de toda la pagina se cargan en lote (@BatchSize en Trabajo) y las fechas de publicacion en una consulta
 * agrupada. Una pagina cuesta como maximo: pagina + count + autores + hashtags + fechas.</p>
 */
@Service
@RequiredArgsConstructor
public class RepositorioService {

    private static final Sort ORDEN_PUBLICACION = Sort.by(Sort.Direction.DESC, "fechaActualizacion", "idTrabajo");

    private final TrabajoRepository trabajoRepository;
    private final DocumentoRepository documentoRepository;
    private final TrabajoHistorialRepository historialRepository;
    private final AccesoTrabajos acceso;
    private final TrabajoMapper mapper;

    @Transactional(readOnly = true)
    public PaginaResponse<TrabajoRepositorioResumenResponse> buscar(FiltrosRepositorio filtros, int pagina,
                                                                    int tamano) {
        Specification<Trabajo> spec = TrabajoSpecs.estado(EstadoTrabajo.EVALUADO)
                .and(TrabajoSpecs.texto(filtros.q()))
                .and(TrabajoSpecs.conHashtag(filtros.hashtag()))
                .and(TrabajoSpecs.facultad(filtros.idFacultad()))
                .and(TrabajoSpecs.carreraDeAlgunAutor(filtros.idCarrera()))
                .and(TrabajoSpecs.materia(filtros.idMateria()))
                .and(TrabajoSpecs.semestre(filtros.idSemestre()))
                .and(TrabajoSpecs.tipoTrabajo(filtros.idTipoTrabajo()));
        Page<Trabajo> page = trabajoRepository.findAll(spec, PageRequest.of(pagina, tamano, ORDEN_PUBLICACION));
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

    /** Ficha publica; 404 (mismo mensaje que un id inexistente) si no existe o no esta EVALUADO. */
    @Transactional(readOnly = true)
    public TrabajoPublicoResponse obtener(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = trabajoRepository.findDetalleByIdTrabajo(idTrabajo)
                .filter(t -> t.getEstado() == EstadoTrabajo.EVALUADO)
                .orElseThrow(() -> AccesoTrabajos.noExiste(idTrabajo));
        Instant fechaPublicacion = historialRepository.findUltimaFechaEstado(idTrabajo, EstadoTrabajo.EVALUADO)
                .orElse(trabajo.getFechaActualizacion());
        return mapper.toPublico(trabajo,
                documentoRepository.findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(idTrabajo),
                fechaPublicacion, actor.idPersona());
    }
}
