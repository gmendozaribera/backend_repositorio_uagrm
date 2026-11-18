package com.uagrm.repositorio.imp.almacenamiento;

import java.io.InputStream;

/**
 * Almacenamiento de objetos (archivos de los trabajos). La implementacion vive en el servicio que la usa
 * (work-service: MinIO); asi el resto de servicios no carga el cliente de MinIO aunque escaneen imp.
 * Si no hay implementacion en el contexto, los services de documentos responden 503.
 *
 * <p>Toda falla de comunicacion debe lanzarse como {@code NegocioException} 503 (regla 6: uploads 503).</p>
 */
public interface AlmacenamientoArchivos {

    /**
     * Sube el contenido en streaming (sin cargarlo entero en memoria).
     *
     * @param clave       key del objeto (trabajos/{idTrabajo}/{uuid}.{ext})
     * @param tamanoBytes tamano exacto del contenido
     */
    void subir(String clave, InputStream contenido, long tamanoBytes, String contentType);

    /** Stream del objeto (quien lo recibe debe cerrarlo). 404 si el objeto no existe. */
    InputStream descargar(String clave);

    /** Borra el objeto; no falla si ya no existe. */
    void borrar(String clave);
}
