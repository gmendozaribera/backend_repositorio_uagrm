package com.uagrm.repositorio.imp.service.catalogo;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.repository.MateriaRepository;
import com.uagrm.repositorio.dao.repository.SemestreRepository;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve las referencias de un catalogo a otro (facultad, materia, semestre) con las reglas comunes:
 * 404 si no existe; 409 si esta inactiva y se la esta asignando de nuevo (mantener la que ya tenia
 * el registro esta permitido, para poder editar registros de una facultad desactivada).
 */
@Component
@RequiredArgsConstructor
public class ReferenciasCatalogo {

    private final FacultadRepository facultadRepository;
    private final MateriaRepository materiaRepository;
    private final SemestreRepository semestreRepository;

    public Facultad facultad(Integer idFacultad, Facultad actual) {
        if (actual != null && Objects.equals(actual.getIdFacultad(), idFacultad)) {
            return actual;
        }
        Facultad facultad = facultadRepository.findById(idFacultad)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la facultad " + idFacultad + "."));
        exigirActivo(facultad.getEstado(), "La facultad " + facultad.getSigla() + " esta inactiva.");
        return facultad;
    }

    public Materia materia(Integer idMateria, Materia actual) {
        if (actual != null && Objects.equals(actual.getIdMateria(), idMateria)) {
            return actual;
        }
        Materia materia = materiaRepository.findById(idMateria)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la materia " + idMateria + "."));
        exigirActivo(materia.getEstado(), "La materia " + materia.getSigla() + " esta inactiva.");
        return materia;
    }

    public Semestre semestre(Integer idSemestre, Semestre actual) {
        if (actual != null && Objects.equals(actual.getIdSemestre(), idSemestre)) {
            return actual;
        }
        Semestre semestre = semestreRepository.findById(idSemestre)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el semestre " + idSemestre + "."));
        exigirActivo(semestre.getEstado(), "El semestre " + semestre.getSemestre() + "/" + semestre.getAnio()
                + " esta inactivo.");
        return semestre;
    }

    /** Siglas sin espacios y en mayusculas: "inf " e "INF" son la misma sigla. */
    static String sigla(String sigla) {
        return sigla.trim().toUpperCase(Locale.ROOT);
    }

    static String texto(String texto) {
        return texto == null ? null : texto.trim();
    }

    /** Para los chequeos de duplicados "que no sea el mismo registro": en una creacion no hay id (0 no existe). */
    static Integer idOCero(Integer id) {
        return id == null ? 0 : id;
    }

    private static void exigirActivo(Integer estado, String detalle) {
        if (!Objects.equals(estado, Estado.ACTIVO)) {
            throw NegocioException.conflicto(detalle);
        }
    }
}
