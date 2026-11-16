package com.uagrm.repositorio.dto.dominio;

/** Validacion comun de grupo y nombre de un parametro (schema CodigoDominio del contrato). */
public final class ReglasDominio {

    public static final String PATRON_CODIGO = "^[A-Za-z][A-Za-z0-9_]*$";
    public static final String MENSAJE_CODIGO = "solo letras, numeros y guion bajo, empezando con una letra";

    private ReglasDominio() {
    }
}
