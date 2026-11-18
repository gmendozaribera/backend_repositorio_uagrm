package com.uagrm.repositorio.dto.trabajo;

import java.time.Instant;
import java.util.List;

/**
 * Tarjeta de un resultado del repositorio (trabajo EVALUADO). Sin estado: en el repositorio todo esta EVALUADO.
 *
 * @param extracto         primeros ~200 caracteres del resumen, cortado en limite de palabra y con "..." si se corto
 * @param autores          nombres completos (creador primero)
 * @param periodo          semestre y anio, p. ej. 2-2026
 * @param fechaPublicacion fecha en que paso a EVALUADO (ultima actualizacion si no hay historial de esa transicion)
 */
public record TrabajoRepositorioResumenResponse(
        Integer idTrabajo,
        String titulo,
        String extracto,
        List<String> hashtags,
        List<String> autores,
        String siglaMateria,
        String nombreMateria,
        String periodo,
        String tipoTrabajo,
        Instant fechaPublicacion) {
}
