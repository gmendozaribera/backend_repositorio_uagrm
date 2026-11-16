package com.uagrm.repositorio.dto.usuario;

/** Validacion comun de passwords temporales y roles en los requests de usuarios. */
public final class ReglasUsuario {

    /** Mismos nombres que tipo_usuario (V2). */
    public static final String PATRON = "ADMIN|DOCENTE|ESTUDIANTE";
    public static final String MENSAJE = "debe ser ADMIN, DOCENTE o ESTUDIANTE";

    /** Misma politica que el cambio de contrasena: 8..72, mayuscula, minuscula y numero. */
    public static final String PATRON_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    public static final String MENSAJE_PASSWORD = "debe contener al menos una mayuscula, una minuscula y un numero";

    private ReglasUsuario() {
    }
}
