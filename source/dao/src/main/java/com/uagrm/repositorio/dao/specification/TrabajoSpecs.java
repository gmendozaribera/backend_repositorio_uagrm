package com.uagrm.repositorio.dao.specification;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Trabajo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de los listados de trabajos ("mis trabajos", bandeja de revision y repositorio). Un valor null = sin
 * filtro. Los filtros sobre colecciones (autores, hashtags) usan EXISTS: no duplican filas y el count de la
 * paginacion queda simple.
 */
public final class TrabajoSpecs {

    /** Funcion SQL de V9 (envoltorio IMMUTABLE de unaccent). */
    private static final String F_UNACCENT = "f_unaccent";
    private static final char ESCAPE = '\\';

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

    // ---------- Repositorio ----------

    public static Specification<Trabajo> facultad(Integer idFacultad) {
        return (root, query, cb) -> idFacultad == null ? cb.conjunction()
                : cb.equal(root.get("materia").get("facultad").get("idFacultad"), idFacultad);
    }

    public static Specification<Trabajo> materia(Integer idMateria) {
        return (root, query, cb) -> idMateria == null ? cb.conjunction()
                : cb.equal(root.get("materia").get("idMateria"), idMateria);
    }

    public static Specification<Trabajo> semestre(Integer idSemestre) {
        return (root, query, cb) -> idSemestre == null ? cb.conjunction()
                : cb.equal(root.get("semestre").get("idSemestre"), idSemestre);
    }

    public static Specification<Trabajo> tipoTrabajo(Integer idTipoTrabajo) {
        return (root, query, cb) -> idTipoTrabajo == null ? cb.conjunction()
                : cb.equal(root.get("tipoTrabajo").get("idTipoTrabajo"), idTipoTrabajo);
    }

    /** Al menos un autor es de la carrera (persona.id_carrera). */
    public static Specification<Trabajo> carreraDeAlgunAutor(Integer idCarrera) {
        return (root, query, cb) -> {
            if (idCarrera == null) {
                return cb.conjunction();
            }
            Subquery<Integer> sub = query.subquery(Integer.class);
            var t = sub.correlate(root);
            Join<Trabajo, Persona> autor = t.join("autores");
            sub.select(autor.get("idPersona")).where(cb.equal(autor.get("carrera").get("idCarrera"), idCarrera));
            return cb.exists(sub);
        };
    }

    /** Hashtag exacto (los hashtags se guardan en minusculas y sin '#'). Blanco = sin filtro. */
    public static Specification<Trabajo> conHashtag(String hashtag) {
        return (root, query, cb) -> {
            if (hashtag == null || hashtag.isBlank()) {
                return cb.conjunction();
            }
            String texto = hashtag.strip().toLowerCase(Locale.ROOT);
            if (texto.startsWith("#")) {
                texto = texto.substring(1);
            }
            Subquery<Integer> sub = query.subquery(Integer.class);
            var t = sub.correlate(root);
            Join<Trabajo, Hashtag> h = t.join("hashtags");
            sub.select(h.get("idHashtag")).where(cb.equal(h.get("texto"), texto));
            return cb.exists(sub);
        };
    }

    /**
     * Texto libre en titulo, resumen o algun hashtag, sin distinguir mayusculas ni tildes:
     * {@code f_unaccent(lower(columna)) LIKE f_unaccent(lower('%texto%'))}. La misma normalizacion (la de la BD)
     * se aplica a ambos lados. Los comodines % y _ que escriba el usuario se escapan: se buscan literalmente.
     */
    public static Specification<Trabajo> texto(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            Expression<String> patron = normalizar(cb, cb.literal("%" + escaparLike(q.strip()) + "%"));
            Subquery<Integer> sub = query.subquery(Integer.class);
            var t = sub.correlate(root);
            Join<Trabajo, Hashtag> h = t.join("hashtags");
            sub.select(h.get("idHashtag")).where(cb.like(normalizar(cb, h.get("texto")), patron, ESCAPE));
            return cb.or(
                    cb.like(normalizar(cb, root.get("titulo")), patron, ESCAPE),
                    cb.like(normalizar(cb, root.get("resumen")), patron, ESCAPE),
                    cb.exists(sub));
        };
    }

    private static Expression<String> normalizar(CriteriaBuilder cb, Expression<String> valor) {
        return cb.function(F_UNACCENT, String.class, cb.lower(valor));
    }

    /** Escapa el caracter de escape y los comodines de LIKE. */
    static String escaparLike(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
