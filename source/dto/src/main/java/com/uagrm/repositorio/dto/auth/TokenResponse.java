package com.uagrm.repositorio.dto.auth;

/**
 * @param expiraEn segundos de vida del access token
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tipoToken,
        long expiraEn,
        UsuarioSesionDto usuario) {
}
