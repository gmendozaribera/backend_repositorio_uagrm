package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.sigla;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.FacultadRequest;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
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
public class FacultadService {

    private static final Sort ORDEN = Sort.by("nombre");

    private final FacultadRepository facultadRepository;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activas desde cache; con incluirInactivos (pantallas admin) siempre desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<FacultadResponse> listar(boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(true);
        }
        return cache.listar(EntidadCatalogo.FACULTADES, null, FacultadResponse.class, () -> consultar(false));
    }

    @Transactional
    public FacultadResponse crear(FacultadRequest request) {
        return guardar(new Facultad(), request);
    }

    @Transactional
    public FacultadResponse actualizar(Integer id, FacultadRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public FacultadResponse desactivar(Integer id) {
        Facultad facultad = buscar(id);
        facultad.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.FACULTADES);
        return mapper.toResponse(facultad);
    }

    private List<FacultadResponse> consultar(boolean incluirInactivos) {
        return facultadRepository.findAll(CatalogoSpecs.<Facultad>activos(incluirInactivos), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private FacultadResponse guardar(Facultad facultad, FacultadRequest request) {
        String sigla = sigla(request.sigla());
        if (facultadRepository.existsBySiglaAndIdFacultadNot(sigla, idOCero(facultad.getIdFacultad()))) {
            throw NegocioException.conflicto("Ya existe una facultad con la sigla " + sigla + ".");
        }
        mapper.actualizar(request, facultad);
        facultad.setNombre(texto(request.nombre()));
        facultad.setSigla(sigla);
        Facultad guardada = facultadRepository.save(facultad);
        cache.invalidar(EntidadCatalogo.FACULTADES);
        return mapper.toResponse(guardada);
    }

    private Facultad buscar(Integer id) {
        return facultadRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la facultad " + id + "."));
    }
}
