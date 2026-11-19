package com.uagrm.repositorio.plagio.copyleaks;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.plagio.MotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Motor COPYLEAKS (PLAGIO_MOTOR=COPYLEAKS). Envia CADA archivo como un scan propio {scanId}-{indice} (la API
 * analiza un archivo por scan) y termina de forma asincrona: Copyleaks avisa por webhook
 * ({@link WebhookCopyleaksService}).
 *
 * <p>Propiedades del scan (supuestos documentados, ver el reporte de la tarea):</p>
 * <ul>
 *   <li>{@code sandbox}: COPYLEAKS_SANDBOX (resultados de prueba, sin creditos).</li>
 *   <li>{@code webhooks.status}: {@code {base}/api/v1/plagio/webhook/copyleaks/{token}/{STATUS}/{scanId}};
 *       Copyleaks reemplaza {STATUS} por completed, error, creditschecked o indexed.</li>
 *   <li>{@code scanning.internet}: ConfigFacultad.comparacionInternet.</li>
 *   <li>Comparacion interna: si hay COPYLEAKS_REPOSITORIO_ID (Private Cloud Hub), cada archivo se indexa ahi y se
 *       compara contra lo ya indexado. NO se usa la base compartida de Copyleaks (Shared Data Hub): los trabajos no
 *       publicados de los estudiantes no deben quedar disponibles para otros clientes de Copyleaks.</li>
 * </ul>
 * El archivo pasa de MinIO a Copyleaks en streaming (se codifica en base64 mientras se envia, sin cargarlo entero en
 * memoria: ver {@link CopyleaksCliente#enviarArchivo}). Sin internet ni repositorio privado no hay contra que
 * comparar: {@link #verificarFuentes} responde 400 al iniciar.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.plagio.motor", havingValue = "COPYLEAKS")
@RequiredArgsConstructor
public class MotorCopyleaks implements MotorPlagio {

    static final String RUTA_WEBHOOK = "/api/v1/plagio/webhook/copyleaks/";
    static final String SIN_FUENTES = "No hay contra que comparar: active la comparacion con internet en la "
            + "configuracion de la facultad o configure el repositorio privado de Copyleaks.";
    private static final int LARGO_NOMBRE = 255;

    private final CopyleaksCliente cliente;
    private final AlmacenamientoArchivos almacenamiento;
    private final PlagioProperties props;

    @Override
    public TipoMotorPlagio tipo() {
        return TipoMotorPlagio.COPYLEAKS;
    }

    /**
     * Copyleaks compara contra internet (si la facultad lo activo) y contra el repositorio privado (si hay
     * COPYLEAKS_REPOSITORIO_ID). Sin ninguno de los dos el scan no compararia contra nada pero igual consumiria
     * creditos: se rechaza con 400 antes de crear el analisis.
     */
    @Override
    public void verificarFuentes(boolean comparacionInternet) {
        if (!comparacionInternet && props.copyleaks().repositorioId().isBlank()) {
            throw NegocioException.datosInvalidos(SIN_FUENTES);
        }
    }

    @Override
    public void enviar(SolicitudAnalisis solicitud) {
        for (SolicitudAnalisis.DocumentoAnalisis documento : solicitud.documentos()) {
            String scanId = solicitud.scanId() + "-" + documento.indice();
            // Cada intento de envio abre de nuevo el archivo en MinIO (el reintento tras un 401 necesita leerlo otra vez)
            cliente.enviarArchivo(scanId, nombre(documento.nombreArchivo()),
                    () -> almacenamiento.descargar(documento.rutaArchivo()),
                    propiedades(scanId, solicitud.comparacionInternet()));
            log.info("Documento {} del analisis {} enviado a Copyleaks (scan {})", documento.indice(),
                    solicitud.idAnalisis(), scanId);
        }
    }

    Map<String, Object> propiedades(String scanId, boolean internet) {
        Map<String, Object> scanning = new LinkedHashMap<>();
        scanning.put("internet", internet);
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("sandbox", props.copyleaks().sandbox());
        propiedades.put("webhooks", Map.of("status", urlWebhook(scanId)));
        String repositorio = props.copyleaks().repositorioId();
        if (!repositorio.isBlank()) {
            scanning.put("repositories", List.of(Map.of("id", repositorio,
                    "includeMySubmissions", true, "includeOthersSubmissions", true)));
            propiedades.put("indexing", Map.of("repositories", List.of(Map.of("id", repositorio))));
        }
        propiedades.put("scanning", scanning);
        return propiedades;
    }

    private String urlWebhook(String scanId) {
        String base = props.webhook().baseUrl().replaceAll("/+$", "");
        return base + RUTA_WEBHOOK + props.webhook().token() + "/{STATUS}/" + scanId;
    }

    private static String nombre(String nombreArchivo) {
        return nombreArchivo.length() <= LARGO_NOMBRE ? nombreArchivo
                : nombreArchivo.substring(nombreArchivo.length() - LARGO_NOMBRE);
    }
}
