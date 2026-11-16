package com.uagrm.repositorio.imp.service.catalogo;

import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.idOCero;
import static com.uagrm.repositorio.imp.service.catalogo.ReferenciasCatalogo.texto;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.GrupoDocenteId;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.GrupoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dto.catalogo.ActualizarDocenteGrupoRequest;
import com.uagrm.repositorio.dto.catalogo.AsignarDocenteRequest;
import com.uagrm.repositorio.dto.catalogo.DocenteGrupoResponse;
import com.uagrm.repositorio.dto.catalogo.GrupoRequest;
import com.uagrm.repositorio.dto.catalogo.GrupoResponse;
import com.uagrm.repositorio.dto.mapper.CatalogoMapper;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grupos (materia + semestre) y asignacion de docentes con el flag esAdminCurso.
 * No se cachean: cambian seguido durante el semestre y se consultan con filtros combinados.
 */
@Service
@RequiredArgsConstructor
public class GrupoService {

    private static final Sort ORDEN = Sort.by("nombre");

    private final GrupoRepository grupoRepository;
    private final GrupoDocenteRepository grupoDocenteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReferenciasCatalogo referencias;
    private final CatalogoMapper mapper;

    @Transactional(readOnly = true)
    public List<GrupoResponse> listar(Integer idFacultad, Integer idMateria, Integer idSemestre,
                                      boolean incluirInactivos) {
        return grupoRepository.findAll(CatalogoSpecs.<Grupo>activos(incluirInactivos)
                        .and(CatalogoSpecs.deFacultadDe("materia", idFacultad))
                        .and(CatalogoSpecs.idDe("materia", "idMateria", idMateria))
                        .and(CatalogoSpecs.idDe("semestre", "idSemestre", idSemestre)), ORDEN).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public GrupoResponse crear(GrupoRequest request) {
        return guardar(new Grupo(), request);
    }

    @Transactional
    public GrupoResponse actualizar(Integer id, GrupoRequest request) {
        return guardar(buscar(id), request);
    }

    @Transactional
    public GrupoResponse desactivar(Integer id) {
        Grupo grupo = buscar(id);
        grupo.setEstado(Estado.INACTIVO);
        return mapper.toResponse(grupo);
    }

    // ---------- Docentes del grupo ----------

    @Transactional(readOnly = true)
    public List<DocenteGrupoResponse> listarDocentes(Integer idGrupo) {
        buscar(idGrupo);
        return grupoDocenteRepository.findByIdIdGrupoOrderByUsuarioPersonaApellidosAsc(idGrupo).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** El usuario debe existir, estar activo y ser DOCENTE. Un docente puede ser admin de curso de N grupos. */
    @Transactional
    public DocenteGrupoResponse asignarDocente(Integer idGrupo, AsignarDocenteRequest request) {
        Grupo grupo = buscar(idGrupo);
        if (!Objects.equals(grupo.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("El grupo " + grupo.getNombre() + " esta inactivo.");
        }
        Usuario usuario = usuarioRepository.findConRolByIdUsuario(request.idUsuario())
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el usuario " + request.idUsuario() + "."));
        if (!TipoUsuario.DOCENTE.equals(usuario.getTipoUsuario().getNombre())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos",
                    "El usuario " + usuario.getUsuario() + " no es docente.");
        }
        if (!Objects.equals(usuario.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("El usuario " + usuario.getUsuario() + " esta desactivado.");
        }
        GrupoDocenteId id = new GrupoDocenteId(idGrupo, usuario.getIdUsuario());
        if (grupoDocenteRepository.existsById(id)) {
            throw NegocioException.conflicto("El docente " + usuario.getUsuario() + " ya esta asignado a este grupo.");
        }
        GrupoDocente asignacion = new GrupoDocente();
        asignacion.setId(id);
        asignacion.setGrupo(grupo);
        asignacion.setUsuario(usuario);
        asignacion.setEsAdminCurso(request.esAdminCurso());
        return mapper.toResponse(grupoDocenteRepository.save(asignacion));
    }

    /** Cambia solo esAdminCurso; la asignacion debe existir. */
    @Transactional
    public DocenteGrupoResponse actualizarDocente(Integer idGrupo, Integer idUsuario,
                                                  ActualizarDocenteGrupoRequest request) {
        GrupoDocente asignacion = grupoDocenteRepository.findById(new GrupoDocenteId(idGrupo, idUsuario))
                .orElseThrow(() -> NegocioException.noEncontrado(
                        "El usuario " + idUsuario + " no esta asignado al grupo " + idGrupo + "."));
        asignacion.setEsAdminCurso(request.esAdminCurso());
        return mapper.toResponse(asignacion);
    }

    @Transactional
    public void quitarDocente(Integer idGrupo, Integer idUsuario) {
        GrupoDocenteId id = new GrupoDocenteId(idGrupo, idUsuario);
        if (!grupoDocenteRepository.existsById(id)) {
            throw NegocioException.noEncontrado("El usuario " + idUsuario + " no esta asignado al grupo " + idGrupo + ".");
        }
        grupoDocenteRepository.deleteById(id);
    }

    // ---------- privados ----------

    /** La materia y el semestre deben ser de la misma facultad; el nombre es unico por materia + semestre. */
    private GrupoResponse guardar(Grupo grupo, GrupoRequest request) {
        Materia materia = referencias.materia(request.idMateria(), grupo.getMateria());
        Semestre semestre = referencias.semestre(request.idSemestre(), grupo.getSemestre());
        if (!Objects.equals(materia.getFacultad().getIdFacultad(), semestre.getFacultad().getIdFacultad())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos",
                    "La materia y el semestre pertenecen a facultades distintas.");
        }
        String nombre = texto(request.nombre());
        if (grupoRepository.existsByMateriaIdMateriaAndSemestreIdSemestreAndNombreAndIdGrupoNot(
                materia.getIdMateria(), semestre.getIdSemestre(), nombre, idOCero(grupo.getIdGrupo()))) {
            throw NegocioException.conflicto("Ya existe el grupo " + nombre + " de la materia " + materia.getSigla()
                    + " en ese semestre.");
        }
        mapper.actualizar(request, grupo);
        grupo.setNombre(nombre);
        grupo.setMateria(materia);
        grupo.setSemestre(semestre);
        return mapper.toResponse(grupoRepository.save(grupo));
    }

    private Grupo buscar(Integer id) {
        return grupoRepository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el grupo " + id + "."));
    }
}
