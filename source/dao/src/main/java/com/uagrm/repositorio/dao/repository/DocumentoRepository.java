package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoRepository extends JpaRepository<Documento, Integer> {

    /** Documentos del detalle del trabajo con su fase, en orden de subida. */
    @EntityGraph(attributePaths = {"fase"})
    List<Documento> findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(Integer idTrabajo);

    /** Documento con su trabajo (para verificar permisos sobre el trabajo). */
    @EntityGraph(attributePaths = {"trabajo", "trabajo.grupo"})
    Optional<Documento> findConTrabajoByIdDocumento(Integer idDocumento);

    /** Enviar a revision exige al menos un archivo con esta extension (pdf). */
    boolean existsByTrabajoIdTrabajoAndTipoAndExtension(Integer idTrabajo, TipoDocumento tipo, String extension);

    /** Archivos del trabajo con alguna de las extensiones dadas (plagio: pdf, docx, odt), en orden de subida. */
    List<Documento> findByTrabajoIdTrabajoAndTipoAndExtensionInOrderByFechaSubidaAscIdDocumentoAsc(
            Integer idTrabajo, TipoDocumento tipo, Collection<String> extensiones);

    /** Keys de MinIO de los archivos del trabajo (para borrarlos despues de eliminar el trabajo). */
    @Query("select d.rutaArchivo from Documento d where d.trabajo.idTrabajo = :idTrabajo and d.rutaArchivo is not null")
    List<String> findRutasByTrabajo(@Param("idTrabajo") Integer idTrabajo);
}
