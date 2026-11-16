package com.uagrm.repositorio.imp.service.padron;

import java.util.Map;

/**
 * Fila de datos leida del archivo, sin validar.
 *
 * @param fila    numero de fila en el archivo (la cabecera es la fila 1)
 * @param valores columna (nombre normalizado de la cabecera) -> texto de la celda, sin espacios a los lados
 */
public record FilaPadron(int fila, Map<String, String> valores) {

    public String valor(String columna) {
        return valores.getOrDefault(columna, "");
    }
}
