package com.uagrm.repositorio.imp.service.notificacion;

/**
 * Textos de las notificaciones del Sprint 2 (titulo hasta 150 y mensaje hasta 500 caracteres).
 * Nunca incluyen contrasenas ni datos que no deban viajar por correo.
 */
public final class TextosNotificacion {

    /** Pantalla del frontend donde se cambia la contrasena y se activa el 2FA. */
    public static final String ENLACE_SEGURIDAD = "/perfil/seguridad";

    public static final String TITULO_BIENVENIDA = "Bienvenido al Repositorio Digital";
    public static final String MENSAJE_BIENVENIDA_REGISTRO =
            "Tu cuenta de estudiante fue creada. Al iniciar sesion deberas cambiar la contrasena que te entrego "
                    + "tu facultad; te recomendamos activar tambien la verificacion en dos pasos.";
    public static final String MENSAJE_BIENVENIDA_ADMIN =
            "El administrador creo tu cuenta. Al iniciar sesion deberas cambiar la contrasena temporal que te "
                    + "entregaron; te recomendamos activar tambien la verificacion en dos pasos.";

    public static final String TITULO_PASSWORD_RESTABLECIDA = "Tu contrasena fue restablecida";
    public static final String MENSAJE_PASSWORD_RESTABLECIDA =
            "El administrador te asigno una contrasena temporal y cerro tus sesiones abiertas. Deberas cambiarla "
                    + "al iniciar sesion. Si no pediste este cambio, avisa a tu facultad.";

    private TextosNotificacion() {
    }
}
