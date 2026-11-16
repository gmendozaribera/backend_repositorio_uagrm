package com.uagrm.repositorio.dto.mapper;

import com.uagrm.repositorio.dao.entity.Notificacion;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.notificacion.NotificacionResponse;
import com.uagrm.repositorio.dto.perfil.Preferencias;
import org.mapstruct.Mapper;

/** Mapeos de notificaciones y preferencias del perfil (el tipo enum sale como su nombre). */
@Mapper
public interface NotificacionMapper {

    NotificacionResponse toResponse(Notificacion notificacion);

    Preferencias toPreferencias(Usuario usuario);
}
