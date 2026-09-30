package com.uagrm.repositorio.dto.auth;

/**
 * @param refreshToken opcional; si se envia, tambien se revoca para que no pueda renovar la sesion
 */
public record LogoutRequest(String refreshToken) {
}
