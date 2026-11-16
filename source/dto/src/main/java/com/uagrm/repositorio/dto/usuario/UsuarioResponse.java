package com.uagrm.repositorio.dto.usuario;

import java.time.LocalDate;

/** Cuenta + datos de la persona. Nunca incluye la contrasena ni el secreto TOTP. */
public record UsuarioResponse(
        Integer idUsuario,
        String usuario,
        String rol,
        Integer estado,
        Boolean requiereCambioPassword,
        Boolean twoFactorEnabled,
        Integer idPersona,
        String nombre,
        String apellidos,
        Integer ci,
        String correo,
        Integer celular,
        LocalDate fechaNac,
        Integer registro,
        Integer idCarrera,
        String siglaCarrera) {
}
