package com.uagrm.repositorio.imp.service.documento;

import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Acceso al almacenamiento de archivos de los trabajos y reglas de consistencia BD ↔ MinIO:
 * <ul>
 *   <li>Key de cada objeto: {@code trabajos/{idTrabajo}/{uuid}.{ext}} (nunca el nombre original).</li>
 *   <li>Compensacion: si la BD falla despues de subir, {@link #borrarSinRomper} borra el objeto huerfano.</li>
 *   <li>Al borrar documentos/trabajos, los objetos se borran DESPUES del commit: si la transaccion se revierte, el
 *       archivo sigue ahi. Si MinIO falla en ese momento solo se loguea (queda un objeto huerfano, nunca un
 *       registro que apunte a un archivo inexistente).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchivosTrabajo {

    private final ObjectProvider<AlmacenamientoArchivos> almacenamiento;

    /** El almacenamiento de este servicio; 503 si el servicio no tiene uno configurado. */
    public AlmacenamientoArchivos almacenamiento() {
        AlmacenamientoArchivos impl = almacenamiento.getIfAvailable();
        if (impl == null) {
            throw NegocioException.noDisponible("El almacenamiento de archivos no esta disponible.");
        }
        return impl;
    }

    public static String nuevaClave(Integer idTrabajo, String extension) {
        return "trabajos/" + idTrabajo + "/" + UUID.randomUUID() + "." + extension;
    }

    /** Compensacion inmediata: borra el objeto y, si falla, solo lo loguea (no tapa el error original). */
    public void borrarSinRomper(String clave) {
        try {
            almacenamiento().borrar(clave);
        } catch (RuntimeException ex) {
            log.error("No se pudo borrar el objeto huerfano {} de MinIO: {}", clave, ex.getMessage());
        }
    }

    /** Borra los objetos despues del commit (al instante si no hay transaccion activa). */
    public void borrarTrasCommit(Collection<String> claves) {
        if (claves.isEmpty()) {
            return;
        }
        List<String> copia = List.copyOf(claves);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    copia.forEach(ArchivosTrabajo.this::borrarSinRomper);
                }
            });
        } else {
            copia.forEach(this::borrarSinRomper);
        }
    }
}
