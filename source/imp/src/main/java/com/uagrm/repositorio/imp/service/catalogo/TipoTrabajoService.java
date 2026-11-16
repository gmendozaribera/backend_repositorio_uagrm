package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dao.repository.TipoTrabajoRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoRequest;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapper;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TipoTrabajoService {

    private static final Sort ORDEN = Sort.by("nombre");

    private final TipoTrabajoRepository tipoTrabajoRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activos desde cache (por facultad o todos); con incluirInactivos (pantallas admin) desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<TipoTrabajoResponse> listar(Integer idFacultad, boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(idFacultad, true);
        }
        return cache.listar(EntidadCatalogo.TIPOS_TRABAJO, idFacultad, TipoTrabajoResponse.class,
                () -> consultar(idFacultad, false));
    }

    @Transactional
    public TipoTrabajoResponse crear(TipoTrabajoRequest request) {
        return guardar(new TipoTrabajo(), request);
    }

    @Transactional
    public TipoTrabajoResponse actualizar(Integer id, TipoTrabajoRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public TipoTrabajoResponse desactivar(Integer id) {
        TipoTrabajo tipoTrabajo = buscar(id);
        tipoTrabajo.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.TIPOS_TRABAJO);
        return mapper.toResponse(tipoTrabajo);
    }

    private List<TipoTrabajoResponse> consultar(Integer idFacultad, boolean incluirInactivos) {
        return tipoTrabajoRepository.findAll(CatalogoSpecs.<TipoTrabajo>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultad(idFacultad)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private TipoTrabajoResponse guardar(TipoTrabajo tipoTrabajo, TipoTrabajoRequest request) {
        Facultad facultad = referencias.facultad(request.idFacultad(), tipoTrabajo.getFacultad());
        String nombre = texto(request.nombre());
        if (tipoTrabajoRepository.existsByFacultadIdFacultadAndNombreIgnoreCaseAndIdTipoTrabajoNot(
                facultad.getIdFacultad(), nombre, idOCero(tipoTrabajo.getIdTipoTrabajo()))) {
            throw NegocioException.conflicto("Ya existe el tipo de trabajo " + nombre
                    + " en la facultad " + facultad.getSigla() + ".");
        }
        mapper.actualizar(request, tipoTrabajo);
        tipoTrabajo.setNombre(nombre);
        tipoTrabajo.setFacultad(facultad);
        TipoTrabajo guardado = tipoTrabajoRepository.save(tipoTrabajo);
        cache.invalidar(EntidadCatalogo.TIPOS_TRABAJO);
        return mapper.toResponse(guardado);
    }

    private TipoTrabajo buscar(Integer id) {
        return tipoTrabajoRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el tipo de trabajo " + id + "."));
    }
}
