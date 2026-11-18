package com.uagrm.repositorio.imp.service.notificacion;

/**
 * Textos de las notificaciones (titulo hasta 150 y mensaje hasta 500 caracteres).
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

    // ---------- Sprint 3: trabajos (el mensaje lleva el titulo del trabajo, hasta 200 caracteres) ----------

    /** Ruta del detalle del trabajo en el frontend. */
    public static String enlaceTrabajo(Integer idTrabajo) {
        return "/trabajos/" + idTrabajo;
    }

    public static final String TITULO_TRABAJO_ENVIADO = "Trabajo enviado a revision";
    public static final String MENSAJE_TRABAJO_ENVIADO = "El trabajo \"%s\" del grupo %s de %s espera tu revision.";

    public static final String TITULO_TRABAJO_EN_REVISION = "Tu trabajo esta en revision";
    public static final String MENSAJE_TRABAJO_EN_REVISION = "Un docente comenzo a revisar el trabajo \"%s\".";

    public static final String TITULO_TRABAJO_APROBADO = "Tu trabajo fue aprobado";
    public static final String MENSAJE_TRABAJO_APROBADO = "El trabajo \"%s\" fue aprobado.";

    public static final String TITULO_TRABAJO_APROBADO_CON_OBSERVACIONES = "Tu trabajo fue aprobado con observaciones";
    public static final String MENSAJE_TRABAJO_APROBADO_CON_OBSERVACIONES =
            "El trabajo \"%s\" fue aprobado con observaciones. Revisalas en el detalle, corrige y vuelve a enviarlo.";

    public static final String TITULO_TRABAJO_RECHAZADO = "Tu trabajo fue rechazado";
    public static final String MENSAJE_TRABAJO_RECHAZADO =
            "El trabajo \"%s\" fue rechazado. Revisa el motivo en el detalle, corrige y vuelve a enviarlo.";

    // ---------- Sprint 4 ----------

    /** Sin la nota: el aviso puede viajar por correo; la calificacion se ve en el detalle, con sesion iniciada. */
    public static final String TITULO_TRABAJO_EVALUADO = "Tu trabajo fue evaluado";
    public static final String MENSAJE_TRABAJO_EVALUADO =
            "El trabajo \"%s\" fue calificado y ya esta publicado en el repositorio. Revisa la nota en el detalle.";

    private TextosNotificacion() {
    }
}
