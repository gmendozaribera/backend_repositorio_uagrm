package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import java.time.Instant;

/** Documento del trabajo. Nunca expone la key de MinIO ni el hash: se descarga por /documentos/{id}/descarga. */
public record DocumentoResponse(
        Integer idDocumento,
        String titulo,
        String descripcion,
        TipoDocumento tipo,
        CategoriaDocumento categoria,
        Integer idFase,
        String nombreFase,
        String nombreArchivo,
        String extension,
        Long tamanoBytes,
        String urlExterno,
        Instant fechaSubida) {
}
