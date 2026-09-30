package com.uagrm.repositorio.dto.auth;

/** Datos del usuario autenticado que el frontend necesita para enrutar segun rol. */
public record UsuarioSesionDto(
        Integer idUsuario,
        String usuario,
        String nombre,
        String apellidos,
        String correo,
        String rol,
        Boolean requiereCambioPassword,
        Boolean twoFactorEnabled) {
}
