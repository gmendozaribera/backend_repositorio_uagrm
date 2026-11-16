package com.uagrm.repositorio.dto.perfil;

import jakarta.validation.constraints.NotNull;

/**
 * Preferencias del usuario autenticado (request del PUT y respuesta del GET/PUT de /api/v1/perfil/preferencias).
 *
 * @param notificarCorreo recibir tambien por correo las notificaciones (solo si SMTP_GROUP.ACTIVO)
 */
public record Preferencias(@NotNull Boolean notificarCorreo) {
}
