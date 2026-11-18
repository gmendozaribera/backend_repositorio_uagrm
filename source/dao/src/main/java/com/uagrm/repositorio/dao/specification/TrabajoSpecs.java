package com.uagrm.repositorio.dao.specification;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.GrupoDocente;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TrabajoBusqueda;
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

    /** Funciones SQL de V10: envoltorios de @@ y ts_rank_cd con websearch_to_tsquery('es_unaccent', q). */
    private static final String F_COINCIDE = "f_busqueda_coincide";
    private static final String F_RELEVANCIA = "f_busqueda_relevancia";

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

    /** Facultad de la materia dentro de la lista (acceso anonimo: solo facultades con acceso externo). */
    public static Specification<Trabajo> facultadEn(Collection<Integer> idsFacultad) {
        return (root, query, cb) -> root.get("materia").get("facultad").get("idFacultad").in(idsFacultad);
    }

    /**
     * Busqueda full-text (V10) en titulo, resumen y hashtags con la configuracion es_unaccent (espanol, raices, sin
     * tildes): {@code f_busqueda_coincide(busqueda, q)} = {@code busqueda @@ websearch_to_tsquery('es_unaccent', q)}.
     * Admite "frase entre comillas", -excluir y OR. q en blanco = sin filtro.
     *
     * <p>Ademas ORDENA la consulta de la pagina por relevancia ({@code ts_rank_cd}: titulo > hashtags > resumen),
     * luego por fecha de publicacion e id (desempate estable). Para que ese orden se respete el Pageable debe ir
     * SIN Sort (Spring Data solo reemplaza el orderBy si el Sort no esta vacio). A la consulta de count no se le
     * agrega orden.</p>
     *
     * <p>La columna busqueda se lee con un join por id a {@link TrabajoBusqueda} (misma tabla, solo lectura): asi
     * {@link Trabajo} no carga el vector en cada consulta. El join es 1 a 1 por clave primaria: no duplica filas.
     * El texto viaja como parametro enlazado (nunca concatenado en el SQL).</p>
     */
    public static Specification<Trabajo> busquedaTexto(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            Join<Trabajo, TrabajoBusqueda> b = root.join(TrabajoBusqueda.class);
            b.on(cb.equal(b.get("idTrabajo"), root.get("idTrabajo")));
            Expression<String> consulta = cb.literal(q.strip());
            if (!esConsultaDeConteo(query.getResultType())) {
                query.orderBy(
                        cb.desc(cb.function(F_RELEVANCIA, Float.class, b.get("busqueda"), consulta)),
                        cb.desc(root.get("fechaActualizacion")),
                        cb.desc(root.get("idTrabajo")));
            }
            return cb.isTrue(cb.function(F_COINCIDE, Boolean.class, b.get("busqueda"), consulta));
        };
    }

    private static boolean esConsultaDeConteo(Class<?> tipoResultado) {
        return Long.class.equals(tipoResultado) || long.class.equals(tipoResultado);
    }
}
