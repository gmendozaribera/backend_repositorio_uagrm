package com.uagrm.repositorio.dao.specification;

import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de los listados de usuarios y del padron. Un valor null (o texto vacio) significa "sin filtro".
 */
public final class UsuarioSpecs {

    private UsuarioSpecs() {
    }

    // ---------- Usuarios ----------

    /**
     * Texto libre: contiene (sin distinguir mayusculas) en usuario, nombre, apellidos o correo;
     * si es numerico, ademas igualdad en CI o registro.
     */
    public static Specification<Usuario> texto(String q) {
        return (root, query, cb) -> {
            String patron = patron(q);
            if (patron == null) {
                return cb.conjunction();
            }
            Path<Persona> persona = root.get("persona");
            List<Predicate> opciones = new ArrayList<>(List.of(
                    contiene(cb, root.get("usuario"), patron),
                    contiene(cb, persona.get("nombre"), patron),
                    contiene(cb, persona.get("apellidos"), patron),
                    contiene(cb, persona.get("correo"), patron)));
            Integer numero = numero(q);
            if (numero != null) {
                opciones.add(cb.equal(persona.get("ci"), numero));
                opciones.add(cb.equal(persona.get("registro"), numero));
            }
            return cb.or(opciones.toArray(Predicate[]::new));
        };
    }

    public static Specification<Usuario> rol(String rol) {
        return (root, query, cb) -> rol == null
                ? cb.conjunction()
                : cb.equal(root.get("tipoUsuario").get("nombre"), rol);
    }

    public static Specification<Usuario> carrera(Integer idCarrera) {
        return (root, query, cb) -> idCarrera == null
                ? cb.conjunction()
                : cb.equal(root.get("persona").get("carrera").get("idCarrera"), idCarrera);
    }

    // ---------- Padron ----------

    /** Texto libre: contiene en nombres, apellidos o correo; si es numerico, igualdad en CI o registro. */
    public static Specification<EstudianteHabilitado> textoPadron(String q) {
        return (root, query, cb) -> {
            String patron = patron(q);
            if (patron == null) {
                return cb.conjunction();
            }
            List<Predicate> opciones = new ArrayList<>(List.of(
                    contiene(cb, root.get("nombres"), patron),
                    contiene(cb, root.get("apellidos"), patron),
                    contiene(cb, root.get("correo"), patron)));
            Integer numero = numero(q);
            if (numero != null) {
                opciones.add(cb.equal(root.get("ci"), numero));
                opciones.add(cb.equal(root.get("registro"), numero));
            }
            return cb.or(opciones.toArray(Predicate[]::new));
        };
    }

    public static Specification<EstudianteHabilitado> habilitado(Boolean habilitado) {
        return (root, query, cb) -> habilitado == null
                ? cb.conjunction()
                : cb.equal(root.get("habilitado"), habilitado);
    }

    /** registrado = existe una persona con el mismo numero de registro (el estudiante ya creo su cuenta). */
    public static Specification<EstudianteHabilitado> registrado(Boolean registrado) {
        return (root, query, cb) -> {
            if (registrado == null) {
                return cb.conjunction();
            }
            Subquery<Integer> personas = query.subquery(Integer.class);
            Root<Persona> persona = personas.from(Persona.class);
            personas.select(persona.get("idPersona"))
                    .where(cb.equal(persona.get("registro"), root.get("registro")));
            return registrado ? cb.exists(personas) : cb.not(cb.exists(personas));
        };
    }

    // ---------- privados ----------

    private static Predicate contiene(CriteriaBuilder cb, Path<String> campo, String patron) {
        return cb.like(cb.lower(campo), patron, '\\');
    }

    /** %texto% en minusculas, escapando los comodines de LIKE que escriba el usuario. */
    private static String patron(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String limpio = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + limpio + "%";
    }

    private static Integer numero(String q) {
        if (q == null) {
            return null;
        }
        try {
            int valor = Integer.parseInt(q.trim());
            return valor > 0 ? valor : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
