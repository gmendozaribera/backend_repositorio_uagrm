package com.uagrm.repositorio.dto.auth;

/**
 * @param secreto  clave para ingreso manual en la app autenticadora
 * @param otpAuthUri URI otpauth:// (estandar de Google Authenticator)
 * @param qrDataUri imagen PNG del QR como data URI, lista para un {@code <img src>}
 */
public record ActivarDosFactoresResponse(String secreto, String otpAuthUri, String qrDataUri) {
}
