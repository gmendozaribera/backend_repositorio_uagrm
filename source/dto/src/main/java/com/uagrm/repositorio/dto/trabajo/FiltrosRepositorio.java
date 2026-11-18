package com.uagrm.repositorio.dto.trabajo;

/**
 * Filtros de la busqueda en el repositorio (query params de GET /api/v1/repositorio y /api/v1/publico/repositorio).
 * null = sin filtro; todos se combinan con Y. Los limites (tamano, minimo 1) los valida el controller.
 *
 * @param q         busqueda full-text en titulo, resumen y hashtags (espanol, sin tildes, con raices; admite
 *                  "frase", -excluir y OR)
 * @param hashtag   hashtag exacto, sin '#'
 * @param idGrupo   grupo del trabajo
 * @param idCarrera al menos un autor es de esta carrera
 */
public record FiltrosRepositorio(
        String q,
        String hashtag,
        Integer idGrupo,
        Integer idFacultad,
        Integer idCarrera,
        Integer idMateria,
        Integer idSemestre,
        Integer idTipoTrabajo) {

    public static FiltrosRepositorio ninguno() {
        return new FiltrosRepositorio(null, null, null, null, null, null, null, null);
    }

    /** Solo texto (comodo para tests y llamadas internas). */
    public static FiltrosRepositorio texto(String q) {
        return new FiltrosRepositorio(q, null, null, null, null, null, null, null);
    }
}
