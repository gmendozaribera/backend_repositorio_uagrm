package com.uagrm.repositorio.dao.specification;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Trabajo;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/** Filtros de los listados de trabajos ("mis trabajos" y bandeja de revision). Un valor null = sin filtro. */
public final class TrabajoSpecs {

    private TrabajoSpecs() {
    }

    /** Trabajos donde la persona es autora o coautora (EXISTS sobre trabajo_autor: sin filas duplicadas). */
    public static Specification<Trabajo> deAutor(Integer idPersona) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            var t = sub.correlate(root);
            Join<Trabajo, Persona> autor = t.join("autores");
            sub.select(autor.get("idPersona")).where(cb.equal(autor.get("idPersona"), idPersona));
            return cb.exists(sub);
        };
    }

    /** Trabajos de los grupos donde el docente esta asignado (grupo_docente). */
    public static Specification<Trabajo> deGruposDelDocente(Integer idUsuario) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            var gd = sub.from(GrupoDocente.class);
            sub.select(gd.get("id").get("idGrupo"))
                    .where(cb.equal(gd.get("id").get("idUsuario"), idUsuario));
            return root.get("grupo").get("idGrupo").in(sub);
        };
    }

    public static Specification<Trabajo> estado(EstadoTrabajo estado) {
        return (root, query, cb) -> estado == null ? cb.conjunction() : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Trabajo> estadoEn(Collection<EstadoTrabajo> estados) {
        return (root, query, cb) -> root.get("estado").in(estados);
    }

    public static Specification<Trabajo> grupo(Integer idGrupo) {
        return (root, query, cb) -> idGrupo == null ? cb.conjunction() : cb.equal(root.get("grupo").get("idGrupo"), idGrupo);
    }
}
