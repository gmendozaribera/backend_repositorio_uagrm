package com.uagrm.repositorio.dto.trabajo;

/**
 * Filtros de la busqueda en el repositorio (query params de GET /api/v1/repositorio). null = sin filtro; todos se
 * combinan con Y. Los limites (tamano, minimo 1) los valida el controller.
 *
 * @param q         texto en titulo, resumen o hashtags (sin distinguir mayusculas ni tildes)
 * @param hashtag   hashtag exacto, sin '#'
 * @param idCarrera al menos un autor es de esta carrera
 */
public record FiltrosRepositorio(
        String q,
        String hashtag,
        Integer idFacultad,
        Integer idCarrera,
        Integer idMateria,
        Integer idSemestre,
        Integer idTipoTrabajo) {

    public static FiltrosRepositorio ninguno() {
        return new FiltrosRepositorio(null, null, null, null, null, null, null);
    }
}
