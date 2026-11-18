package com.uagrm.repositorio.plagio.motor;

import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.imp.service.plagio.FuenteDetectada;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculo DETERMINISTICO del motor SIMULADO (sin red, solo desarrollo): todo sale del SHA-256 del documento, asi
 * que el mismo archivo da siempre el mismo porcentaje (el trabajo, el maximo de sus documentos) y las mismas
 * fuentes mientras el repositorio no cambie.
 * <ul>
 *   <li>Porcentaje: 0.00 a 85.00 (cubre los tres niveles del semaforo con los umbrales por defecto 30/50).</li>
 *   <li>Fuentes INTERNA: hasta 2 trabajos REALES de la misma facultad (los candidatos que recibe), con un
 *       porcentaje menor al del documento. Si no estan publicados se ocultan al responder (regla del contrato).</li>
 *   <li>Fuentes INTERNET (solo si la facultad compara con internet): 2 urls de ejemplo https://example.org/...</li>
 *   <li>Un documento con menos de 1 % no tiene fuentes.</li>
 * </ul>
 */
public final class SimuladorPlagio {

    /** Trabajo candidato a fuente interna (id y titulo reales). */
    public record Candidato(Integer idTrabajo, String titulo) {
    }

    /** Resultado de un documento. */
    public record Resultado(BigDecimal porcentaje, List<FuenteDetectada> fuentes) {
    }

    static final String URL_EJEMPLO = "https://example.org/simulado/";
    private static final int MAXIMO_CENTESIMAS = 8500;

    private SimuladorPlagio() {
    }

    public static Resultado analizar(String sha256Documento, List<Candidato> candidatos, boolean internet) {
        byte[] semilla = semilla(sha256Documento);
        BigDecimal porcentaje = BigDecimal.valueOf(entero(semilla, 0) % (MAXIMO_CENTESIMAS + 1), 2);
        List<FuenteDetectada> fuentes = new ArrayList<>();
        if (porcentaje.compareTo(BigDecimal.ONE) < 0) {
            return new Resultado(porcentaje, fuentes);
        }
        if (!candidatos.isEmpty()) {
            Set<Integer> elegidos = new LinkedHashSet<>();
            for (int k = 0; k < 2; k++) {
                elegidos.add((semilla[2 + k] & 0xff) % candidatos.size());
            }
            int k = 0;
            for (Integer indice : elegidos) {
                Candidato candidato = candidatos.get(indice);
                fuentes.add(new FuenteDetectada(TipoFuentePlagio.INTERNA, candidato.titulo(), null,
                        candidato.idTrabajo(), fraccion(porcentaje, 40 + (semilla[4 + k++] & 0xff) % 56)));
            }
        }
        if (internet) {
            String prefijo = HexFormat.of().formatHex(semilla).substring(0, 12);
            for (int k = 0; k < 2; k++) {
                fuentes.add(new FuenteDetectada(TipoFuentePlagio.INTERNET, "Fuente de internet simulada " + (k + 1),
                        URL_EJEMPLO + prefijo + "/" + (k + 1), null,
                        fraccion(porcentaje, 20 + (semilla[6 + k] & 0xff) % 60)));
            }
        }
        return new Resultado(porcentaje, fuentes);
    }

    /** porcentaje * pct / 100 con 2 decimales. */
    private static BigDecimal fraccion(BigDecimal porcentaje, int pct) {
        return porcentaje.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static int entero(byte[] bytes, int desde) {
        return ((bytes[desde] & 0xff) << 8) | (bytes[desde + 1] & 0xff);
    }

    private static byte[] semilla(String sha256Documento) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("simulado|" + sha256Documento).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
