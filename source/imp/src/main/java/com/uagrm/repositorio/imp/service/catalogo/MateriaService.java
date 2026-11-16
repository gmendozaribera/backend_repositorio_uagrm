package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.sigla;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.repository.MateriaRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.MateriaRequest;
import com.uagrm.repositorio.dto.catalogo.MateriaResponse;
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
public class MateriaService {

    private static final Sort ORDEN = Sort.by("sigla");

    private final MateriaRepository materiaRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;
    private final CatalogoCacheService cache;

    /** Activos desde cache (por facultad o todos); con incluirInactivos (pantallas admin) desde PostgreSQL. */
    @Transactional(readOnly = true)
    public List<MateriaResponse> listar(Integer idFacultad, boolean incluirInactivos) {
        if (incluirInactivos) {
            return consultar(idFacultad, true);
        }
        return cache.listar(EntidadCatalogo.MATERIAS, idFacultad, MateriaResponse.class,
                () -> consultar(idFacultad, false));
    }

    @Transactional
    public MateriaResponse crear(MateriaRequest request) {
        return guardar(new Materia(), request);
    }

    @Transactional
    public MateriaResponse actualizar(Integer id, MateriaRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public MateriaResponse desactivar(Integer id) {
        Materia materia = buscar(id);
        materia.setEstado(Estado.INACTIVO);
        cache.invalidar(EntidadCatalogo.MATERIAS);
        return mapper.toResponse(materia);
    }

    private List<MateriaResponse> consultar(Integer idFacultad, boolean incluirInactivos) {
        return materiaRepository.findAll(CatalogoSpecs.<Materia>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultad(idFacultad)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private MateriaResponse guardar(Materia materia, MateriaRequest request) {
        Facultad facultad = referencias.facultad(request.idFacultad(), materia.getFacultad());
        String sigla = sigla(request.sigla());
        if (materiaRepository.existsByFacultadIdFacultadAndSiglaAndIdMateriaNot(
                facultad.getIdFacultad(), sigla, idOCero(materia.getIdMateria()))) {
            throw NegocioException.conflicto("Ya existe una materia con la sigla " + sigla
                    + " en la facultad " + facultad.getSigla() + ".");
        }
        mapper.actualizar(request, materia);
        materia.setNombre(texto(request.nombre()));
        materia.setSigla(sigla);
        materia.setFacultad(facultad);
        Materia guardado = materiaRepository.save(materia);
        cache.invalidar(EntidadCatalogo.MATERIAS);
        return mapper.toResponse(guardado);
    }

    private Materia buscar(Integer id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la materia " + id + "."));
    }
}
