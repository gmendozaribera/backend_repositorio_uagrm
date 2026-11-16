package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.repository.SemestreRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.SemestreRequest;
import com.uagrm.repositorio.dto.catalogo.SemestreResponse;
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
public class SemestreService {

    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "anio", "semestre");

    private final SemestreRepository semestreRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activos desde cache (por facultad o todos); con incluirInactivos (pantallas admin) desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<SemestreResponse> listar(Integer idFacultad, boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(idFacultad, true);
        }
        return cache.listar(EntidadCatalogo.SEMESTRES, idFacultad, SemestreResponse.class,
                () -> consultar(idFacultad, false));
    }

    @Transactional
    public SemestreResponse crear(SemestreRequest request) {
        return guardar(new Semestre(), request);
    }

    @Transactional
    public SemestreResponse actualizar(Integer id, SemestreRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public SemestreResponse desactivar(Integer id) {
        Semestre semestre = buscar(id);
        semestre.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.SEMESTRES);
        return mapper.toResponse(semestre);
    }

    private List<SemestreResponse> consultar(Integer idFacultad, boolean incluirInactivos) {
        return semestreRepository.findAll(CatalogoSpecs.<Semestre>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultad(idFacultad)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private SemestreResponse guardar(Semestre semestre, SemestreRequest request) {
        Facultad facultad = referencias.facultad(request.idFacultad(), semestre.getFacultad());
        if (semestreRepository.existsByFacultadIdFacultadAndAnioAndSemestreAndIdSemestreNot(
                facultad.getIdFacultad(), request.anio(), request.semestre(), idOCero(semestre.getIdSemestre()))) {
            throw NegocioException.conflicto("Ya existe el semestre " + request.semestre() + "/" + request.anio()
                    + " en la facultad " + facultad.getSigla() + ".");
        }
        mapper.actualizar(request, semestre);
        semestre.setFacultad(facultad);
        Semestre guardado = semestreRepository.save(semestre);
        cache.invalidar(EntidadCatalogo.SEMESTRES);
        return mapper.toResponse(guardado);
    }

    private Semestre buscar(Integer id) {
        return semestreRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el semestre " + id + "."));
    }
}
