package com.uagrm.repositorio.imp.service.plagio;

import java.util.List;

/**
 * Lo que un motor necesita para analizar (copia sin entidades JPA: se usa fuera de la transaccion).
 *
 * @param scanId     prefijo de los scans del proveedor externo; null en SIMULADO
 * @param documentos SOLO los documentos que aun no tienen resultado (al reenviar tras un reinicio)
 */
public record SolicitudAnalisis(Integer idAnalisis, Integer idTrabajo, Integer idFacultad, String scanId,
                                boolean comparacionInternet, List<DocumentoAnalisis> documentos) {

    /**
     * @param indice      1..n; el scan del documento en Copyleaks es {scanId}-{indice}
     * @param rutaArchivo key del objeto en MinIO
     */
    public record DocumentoAnalisis(int indice, String nombreArchivo, String rutaArchivo, String sha256) {
    }
}
