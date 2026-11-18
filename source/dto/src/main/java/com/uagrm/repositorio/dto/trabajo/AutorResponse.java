package com.uagrm.repositorio.dto.trabajo;

/** @param esUsuarioActual true si este autor es la persona del usuario autenticado */
public record AutorResponse(Integer idPersona, String nombre, String apellidos, Integer registro,
                            boolean esUsuarioActual) {
}
