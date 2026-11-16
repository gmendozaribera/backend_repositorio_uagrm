package com.uagrm.repositorio.dto.padron;

/**
 * Error de una fila del archivo importado.
 *
 * @param fila    numero de fila en el archivo (la cabecera es la fila 1)
 * @param columna columna con el error; null si el error es de toda la fila
 */
public record ErrorImportacion(int fila, String columna, String mensaje) {
}
