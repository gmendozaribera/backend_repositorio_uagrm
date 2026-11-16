package com.uagrm.repositorio.dao.specification;

import com.uagrm.repositorio.dao.entity.Estado;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros reutilizables para los listados de catalogos. Un valor null significa "sin filtro".
 */
public final class CatalogoSpecs {

    private CatalogoSpecs() {
    }

    /** Solo activos, o todos si incluirInactivos es true. */
    public static <T> Specification<T> activos(boolean incluirInactivos) {
        return (root, query, cb) -> incluirInactivos ? cb.conjunction() : cb.equal(root.get("estado"), Estado.ACTIVO);
    }

    /** relacion.atributoId = valor, por ejemplo facultad.idFacultad = 1. */
    public static <T> Specification<T> idDe(String relacion, String atributoId, Integer valor) {
        return (root, query, cb) -> valor == null
                ? cb.conjunction()
                : cb.equal(root.get(relacion).get(atributoId), valor);
    }

    /** relacion.facultad.idFacultad = valor, por ejemplo los grupos cuya materia es de la facultad 1. */
    public static <T> Specification<T> deFacultadDe(String relacion, Integer idFacultad) {
        return (root, query, cb) -> idFacultad == null
                ? cb.conjunction()
                : cb.equal(root.get(relacion).get("facultad").get("idFacultad"), idFacultad);
    }

    public static <T> Specification<T> deFacultad(Integer idFacultad) {
        return idDe("facultad", "idFacultad", idFacultad);
    }
}
