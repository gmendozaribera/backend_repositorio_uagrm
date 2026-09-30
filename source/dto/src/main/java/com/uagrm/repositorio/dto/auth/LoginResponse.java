package com.uagrm.repositorio.dto.auth;

/**
 * Resultado del paso 1 del login.
 * Sin 2FA: {@code tokens} viene lleno. Con 2FA: {@code requiereDosFactores = true} y el cliente
 * debe enviar {@code tokenTemporal} + codigo TOTP a /api/v1/auth/2fa/verify.
 */
public record LoginResponse(boolean requiereDosFactores, String tokenTemporal, TokenResponse tokens) {

    public static LoginResponse completo(TokenResponse tokens) {
        return new LoginResponse(false, null, tokens);
    }

    public static LoginResponse pendienteDosFactores(String tokenTemporal) {
        return new LoginResponse(true, tokenTemporal, null);
    }
}
