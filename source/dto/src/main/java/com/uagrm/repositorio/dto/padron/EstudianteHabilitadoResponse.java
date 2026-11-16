package com.uagrm.repositorio.dto.padron;

/** Fila del padron. Nunca incluye la contrasena por defecto (ni su hash). */
public record EstudianteHabilitadoResponse(
        Integer id,
        Integer registro,
        String ci,
        String correo,
        String nombres,
        String apellidos,
        Integer idCarrera,
        String siglaCarrera,
        Boolean habilitado,
        Boolean registrado) {
}
