package com.uagrm.repositorio.dao.entity;

/**
 * Categoria de un documento (columna {@code documento.categoria}). Formatos y hosts permitidos por categoria:
 * imp ReglasDocumentos. No renombrar valores (contrato work-api.yaml y filas guardadas).
 */
public enum CategoriaDocumento {
    DOCUMENTO,
    PRESENTACION,
    CODIGO,
    VIDEO,
    IMAGEN
}
