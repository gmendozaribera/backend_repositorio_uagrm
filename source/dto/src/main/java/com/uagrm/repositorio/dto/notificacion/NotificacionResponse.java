package com.uagrm.repositorio.dto.notificacion;

import java.time.Instant;

/**
 * Notificacion in-app.
 *
 * @param tipo         codigo del evento (TipoNotificacion: BIENVENIDA, PASSWORD_RESTABLECIDA...)
 * @param enlace       ruta interna del frontend o null
 * @param fechaLectura null mientras no se lea
 */
public record NotificacionResponse(
        Integer idNotificacion,
        String tipo,
        String titulo,
        String mensaje,
        String enlace,
        boolean leida,
        Instant fechaCreacion,
        Instant fechaLectura) {
}
