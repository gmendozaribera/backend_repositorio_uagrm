package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import java.time.Instant;

/**
 * @param estadoAnterior null en la creacion
 * @param usuario        nombre y apellidos de quien hizo el cambio
 */
public record CambioEstadoResponse(String estadoAnterior, EstadoTrabajo estadoNuevo, String usuario,
                                   String comentario, Instant fecha) {
}
