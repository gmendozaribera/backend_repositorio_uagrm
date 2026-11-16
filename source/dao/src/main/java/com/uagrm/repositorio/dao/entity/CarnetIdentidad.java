package com.uagrm.repositorio.dao.entity;

import java.util.Locale;

/**
 * Reglas del carnet de identidad (CI), compartidas por entidades, DTOs, busquedas, padron y auto-registro.
 * El CI es texto: numero de 4 a 12 digitos con complemento opcional (p. ej. 1234567 o 1234567-1A).
 * Se guarda sin espacios y en MAYUSCULAS para que "1234567-1a" y "1234567-1A" sean el mismo.
 */
public final class CarnetIdentidad {

    /** Mismo patron que el contrato (user-api y auth-api). */
    public static final String PATRON = "^[0-9]{4,12}(-?[0-9A-Za-z]{1,3})?$";
    public static final String MENSAJE =
            "debe ser un CI de 4 a 12 digitos con complemento opcional (p. ej. 1234567 o 1234567-1A)";
    public static final int LONGITUD_MAXIMA = 20;

    /** Celular: solo digitos, con prefijo internacional opcional (mismo patron que el contrato). */
    public static final String PATRON_CELULAR = "^\\+?[0-9]{7,15}$";
    public static final String MENSAJE_CELULAR = "solo digitos, con prefijo internacional opcional (p. ej. +59170012345)";

    private CarnetIdentidad() {
    }

    /** Sin espacios (ni a los lados ni en medio) y en mayusculas; null queda null. */
    public static String normalizar(String ci) {
        return ci == null ? null : ci.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** true si cumple el patron (se espera el valor ya normalizado). */
    public static boolean esValido(String ci) {
        return ci != null && ci.matches(PATRON);
    }
}
