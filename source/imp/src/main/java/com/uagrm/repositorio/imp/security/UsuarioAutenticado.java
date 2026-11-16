package com.uagrm.repositorio.imp.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Identidad del usuario que hace el request, tomada SIEMPRE del access token ya validado (claim sub).
 *
 * <p>En terminos simples: los endpoints "mis notificaciones" o "mis preferencias" nunca reciben el id del
 * usuario por URL ni por el cuerpo; si lo hicieran, cualquiera podria cambiar el numero y leer lo de otro.
 * El token esta firmado por auth-service y no se puede alterar sin que la firma deje de coincidir.</p>
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    /** id_usuario del token (sub). */
    public static Integer id(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }
}
