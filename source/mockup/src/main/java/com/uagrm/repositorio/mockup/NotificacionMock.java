package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.Notificacion;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dto.notificacion.NotificacionResponse;
import java.time.Instant;

/** Builders de notificaciones de prueba. */
public final class NotificacionMock {

    public static final Instant CREADA = Instant.parse("2026-11-16T14:00:00Z");

    private NotificacionMock() {
    }

    public static Notificacion noLeida(Integer id, Integer idUsuario) {
        Notificacion notificacion = new Notificacion();
        notificacion.setIdNotificacion(id);
        notificacion.setIdUsuario(idUsuario);
        notificacion.setTipo(TipoNotificacion.BIENVENIDA);
        notificacion.setTitulo("Bienvenido al Repositorio Digital");
        notificacion.setMensaje("Tu cuenta fue creada.");
        notificacion.setEnlace("/perfil/seguridad");
        notificacion.setLeida(Boolean.FALSE);
        notificacion.setFechaCreacion(CREADA);
        return notificacion;
    }

    public static NotificacionResponse response(Integer id, boolean leida) {
        return new NotificacionResponse(id, TipoNotificacion.BIENVENIDA.name(), "Bienvenido al Repositorio Digital",
                "Tu cuenta fue creada.", leida ? null : "/perfil/seguridad", leida, CREADA,
                leida ? CREADA.plusSeconds(60) : null);
    }
}
