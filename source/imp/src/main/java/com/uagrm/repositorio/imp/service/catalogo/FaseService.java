package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Fase;
import com.uagrm.repositorio.dao.repository.FaseRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.FaseRequest;
import com.uagrm.repositorio.dto.catalogo.FaseResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapper;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Las fases se listan en orden de creacion (orden natural de entrega: perfil, avance, final...). */
@Service
@RequiredArgsConstructor
public class FaseService {

    private static final Sort ORDEN = Sort.by("idFase");

    private final FaseRepository faseRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activos desde cache (por facultad o todos); con incluirInactivos (pantallas admin) desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<FaseResponse> listar(Integer idFacultad, boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(idFacultad, true);
        }
        return cache.listar(EntidadCatalogo.FASES, idFacultad, FaseResponse.class,
                () -> consultar(idFacultad, false));
    }

    @Transactional
    public FaseResponse crear(FaseRequest request) {
        return guardar(new Fase(), request);
    }

    @Transactional
    public FaseResponse actualizar(Integer id, FaseRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public FaseResponse desactivar(Integer id) {
        Fase fase = buscar(id);
        fase.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.FASES);
        return mapper.toResponse(fase);
    }

    private List<FaseResponse> consultar(Integer idFacultad, boolean incluirInactivos) {
        return faseRepository.findAll(CatalogoSpecs.<Fase>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultad(idFacultad)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private FaseResponse guardar(Fase fase, FaseRequest request) {
        Facultad facultad = referencias.facultad(request.idFacultad(), fase.getFacultad());
        String nombre = texto(request.nombre());
        if (faseRepository.existsByFacultadIdFacultadAndNombreIgnoreCaseAndIdFaseNot(
                facultad.getIdFacultad(), nombre, idOCero(fase.getIdFase()))) {
            throw NegocioException.conflicto("Ya existe la fase " + nombre
                    + " en la facultad " + facultad.getSigla() + ".");
        }
        mapper.actualizar(request, fase);
        fase.setNombre(nombre);
        fase.setDescripcion(texto(request.descripcion()));
        fase.setFacultad(facultad);
        Fase guardado = faseRepository.save(fase);
        cache.invalidar(EntidadCatalogo.FASES);
        return mapper.toResponse(guardado);
    }

    private Fase buscar(Integer id) {
        return faseRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la fase " + id + "."));
    }
}
