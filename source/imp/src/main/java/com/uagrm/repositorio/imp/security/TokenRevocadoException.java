package com.uagrm.repositorio.imp.security;

import org.springframework.security.core.AuthenticationException;

/** El token tiene firma y fecha validas, pero fue revocado (logout o cambio de contrasena). */
public class TokenRevocadoException extends AuthenticationException {

    public TokenRevocadoException() {
        super("Token revocado");
    }
}
