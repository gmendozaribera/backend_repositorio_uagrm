package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.sigla;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.CarreraRequest;
import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapper;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.CatalogoCacheService;
import com.uagrm.repositorio.imp.redis.EntidadCatalogo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La sigla de carrera es unica en todo el sistema: la usa el padron de estudiantes (siglaCarrera). */
@Service
@RequiredArgsConstructor
public class CarreraService {

    private static final Sort ORDEN = Sort.by("nombre");

    private final CarreraRepository carreraRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activos desde cache (por facultad o todos); con incluirInactivos (pantallas admin) desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<CarreraResponse> listar(Integer idFacultad, boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(idFacultad, true);
        }
        return cache.listar(EntidadCatalogo.CARRERAS, idFacultad, CarreraResponse.class,
                () -> consultar(idFacultad, false));
    }

    @Transactional
    public CarreraResponse crear(CarreraRequest request) {
        return guardar(new Carrera(), request);
    }

    @Transactional
    public CarreraResponse actualizar(Integer id, CarreraRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public CarreraResponse desactivar(Integer id) {
        Carrera carrera = buscar(id);
        carrera.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.CARRERAS);
        return mapper.toResponse(carrera);
    }

    private List<CarreraResponse> consultar(Integer idFacultad, boolean incluirInactivos) {
        return carreraRepository.findAll(CatalogoSpecs.<Carrera>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultad(idFacultad)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private CarreraResponse guardar(Carrera carrera, CarreraRequest request) {
        Facultad facultad = referencias.facultad(request.idFacultad(), carrera.getFacultad());
        String sigla = sigla(request.sigla());
        if (carreraRepository.existsBySiglaAndIdCarreraNot(sigla, idOCero(carrera.getIdCarrera()))) {
            throw NegocioException.conflicto("Ya existe una carrera con la sigla " + sigla + ".");
        }
        mapper.actualizar(request, carrera);
        carrera.setNombre(texto(request.nombre()));
        carrera.setSigla(sigla);
        carrera.setFacultad(facultad);
        Carrera guardado = carreraRepository.save(carrera);
        cache.invalidar(EntidadCatalogo.CARRERAS);
        return mapper.toResponse(guardado);
    }

    private Carrera buscar(Integer id) {
        return carreraRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la carrera " + id + "."));
    }
}
