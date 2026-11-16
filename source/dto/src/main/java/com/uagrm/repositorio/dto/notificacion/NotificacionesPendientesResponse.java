package com.uagrm.repositorio.dto.notificacion;

/**
 * Contador de la campana.
 *
 * @param cantidad        notificaciones sin leer
 * @param pollingSegundos segundos recomendados hasta la siguiente consulta (NOTIF_GROUP.POLLING_SEGUNDOS)
 */
public record NotificacionesPendientesResponse(long cantidad, int pollingSegundos) {
}
