package com.uagrm.repositorio.dto.padron;

import java.util.List;

/**
 * @param totalFilas  filas de datos leidas (sin la cabecera ni filas vacias)
 * @param conErrores  filas rechazadas (una fila puede tener varios errores en {@code errores})
 */
public record ResultadoImportacion(int totalFilas, int insertados, int actualizados, int conErrores,
                                   List<ErrorImportacion> errores) {
}
