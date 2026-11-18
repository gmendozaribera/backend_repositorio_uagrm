package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;

/**
 * Usuario que hace el request, cargado de la BD a partir del id del token (nunca de la URL ni del cuerpo).
 * El rol se toma de la BD: es el dato vigente aunque el token se haya emitido antes de un cambio.
 */
public record ActorTrabajo(Usuario usuario) {

    public Integer idUsuario() {
        return usuario.getIdUsuario();
    }

    public Persona persona() {
        return usuario.getPersona();
    }

    public Integer idPersona() {
        return usuario.getPersona().getIdPersona();
    }

    public String rol() {
        return usuario.getTipoUsuario().getNombre();
    }

    public boolean esEstudiante() {
        return TipoUsuario.ESTUDIANTE.equals(rol());
    }

    public boolean esDocente() {
        return TipoUsuario.DOCENTE.equals(rol());
    }

    public boolean esAdmin() {
        return TipoUsuario.ADMIN.equals(rol());
    }
}
