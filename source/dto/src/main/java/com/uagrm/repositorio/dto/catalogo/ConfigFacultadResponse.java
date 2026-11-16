package com.uagrm.repositorio.dto.catalogo;

/**
 * Configuracion vigente de una facultad.
 *
 * @param personalizada false si la facultad aun no guardo su configuracion y se usan los valores por defecto
 *                      del grupo FACULTAD_DEFAULT_GROUP de la tabla de dominio
 */
public record ConfigFacultadResponse(
        Integer idFacultad,
        boolean personalizada,
        int tamanoMaxArchivoMB,
        int umbralPlagioMedio,
        int umbralPlagioAlto,
        boolean comparacionInternet,
        boolean accesoExterno,
        int escalaCalifMax,
        int jwtExpiracionHoras) {
}
