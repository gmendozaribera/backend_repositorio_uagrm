package com.uagrm.repositorio.plagio.copyleaks;

import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.plagio.FuenteDetectada;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Avisos (webhooks) de Copyleaks: {@code POST /api/v1/plagio/webhook/copyleaks/{token}/{evento}/{scanId}}.
 *
 * <p><b>Seguridad, en terminos simples:</b> esta URL no pide sesion porque la llama Copyleaks, no un usuario. Para
 * que nadie mas pueda "inventar" resultados, la URL lleva un token secreto (PLAGIO_WEBHOOK_TOKEN, 32+ caracteres)
 * que solo conocen este servicio y Copyleaks (se lo damos al enviar cada archivo):</p>
 * <ul>
 *   <li>Se compara en tiempo constante ({@link MessageDigest#isEqual} sobre el SHA-256 de ambos, asi tambien el
 *       largo queda oculto): medir cuanto tarda la respuesta no revela cuantos caracteres se acertaron.</li>
 *   <li>Token incorrecto → 404, igual que una ruta que no existe: quien prueba no sabe ni que el webhook existe.
 *       Un scanId desconocido tambien → 404.</li>
 *   <li>Nunca se loguean el token ni el cuerpo (trae fragmentos de los documentos); solo evento, scan y numeros.</li>
 *   <li>Idempotente: Copyleaks reintenta los avisos; uno repetido, o que llega con el analisis ya terminado,
 *       responde 200 sin cambiar nada.</li>
 * </ul>
 *
 * <p><b>Interpretacion del cuerpo (supuestos documentados):</b> {@code completed}: porcentaje del documento =
 * {@code results.score.aggregatedScore}; cada fuente = {@code matchedWords / scannedDocument.totalWords}.
 * {@code results.internet} → INTERNET; {@code results.repositories} y {@code results.database} → INTERNA (si su
 * {@code scanId} es de un analisis nuestro se identifica el trabajo). {@code error} → el analisis pasa a ERROR.
 * {@code creditschecked} / {@code indexed} se aceptan sin cambios.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookCopyleaksService {

    private final PlagioService plagioService;
    private final PlagioProperties props;

    public void procesar(String token, String evento, String scanId, JsonNode cuerpo) {
        if (!tokenValido(token)) {
            throw noEncontrado();
        }
        int separador = scanId.lastIndexOf('-');
        if (separador <= 0) {
            throw noEncontrado();
        }
        String prefijo = scanId.substring(0, separador);
        int indice;
        try {
            indice = Integer.parseInt(scanId.substring(separador + 1));
        } catch (NumberFormatException ex) {
            throw noEncontrado();
        }
        Integer idAnalisis = plagioService.analisisPorScan(prefijo).orElseThrow(WebhookCopyleaksService::noEncontrado);

        switch (evento.toLowerCase(Locale.ROOT)) {
            case "completed" -> {
                BigDecimal porcentaje = numero(cuerpo.path("results").path("score").path("aggregatedScore"));
                List<FuenteDetectada> fuentes = fuentes(cuerpo);
                log.info("Copyleaks completo el scan {} (analisis {}): {} % y {} fuentes", scanId, idAnalisis,
                        porcentaje, fuentes.size());
                plagioService.registrarDocumento(idAnalisis, indice, porcentaje, fuentes);
            }
            case "error" -> {
                // Solo el codigo: el mensaje de Copyleaks podria traer datos del documento
                log.warn("Copyleaks informo error en el scan {} (analisis {}), codigo {}", scanId, idAnalisis,
                        cuerpo.path("error").path("code").asString("?"));
                plagioService.fallar(idAnalisis, PlagioService.MENSAJE_PROVEEDOR);
            }
            case "creditschecked", "indexed" -> log.info("Aviso {} de Copyleaks para el scan {}: sin cambios",
                    evento, scanId);
            default -> throw NegocioException.datosInvalidos("Evento de webhook no reconocido.");
        }
    }

    /** Compara el token recibido con el configurado en tiempo constante. Sin token configurado: siempre false. */
    boolean tokenValido(String recibido) {
        String esperado = props.webhook().token();
        if (esperado.isEmpty() || recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(recibido), sha256(esperado));
    }

    private List<FuenteDetectada> fuentes(JsonNode cuerpo) {
        long totalPalabras = cuerpo.path("scannedDocument").path("totalWords").asLong(0);
        JsonNode resultados = cuerpo.path("results");
        List<FuenteDetectada> fuentes = new ArrayList<>();
        for (JsonNode r : resultados.path("internet")) {
            fuentes.add(new FuenteDetectada(TipoFuentePlagio.INTERNET, texto(r, "title"), texto(r, "url"), null,
                    porcentaje(r, totalPalabras)));
        }
        for (String grupo : List.of("repositories", "database")) {
            for (JsonNode r : resultados.path(grupo)) {
                fuentes.add(new FuenteDetectada(TipoFuentePlagio.INTERNA, texto(r, "title"), null,
                        trabajoDe(texto(r, "scanId")), porcentaje(r, totalPalabras)));
            }
        }
        return fuentes;
    }

    /** Trabajo de un scan nuestro ({prefijo}-{indice}); null si el scan no es de este sistema. */
    private Integer trabajoDe(String scanIdFuente) {
        if (scanIdFuente == null) {
            return null;
        }
        int separador = scanIdFuente.lastIndexOf('-');
        if (separador <= 0) {
            return null;
        }
        return plagioService.trabajoPorScan(scanIdFuente.substring(0, separador)).orElse(null);
    }

    private static BigDecimal porcentaje(JsonNode resultado, long totalPalabras) {
        if (totalPalabras <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(resultado.path("matchedWords").asLong(0) * 100L)
                .divide(BigDecimal.valueOf(totalPalabras), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal numero(JsonNode nodo) {
        return nodo.isNumber() ? nodo.decimalValue() : BigDecimal.ZERO;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static NegocioException noEncontrado() {
        return NegocioException.noEncontrado("No encontrado.");
    }
}
