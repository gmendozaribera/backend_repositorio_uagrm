package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.AnalisisPlagioDocumento;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.FuentePlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dto.plagio.AnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoAnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.FuentePlagioResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Builders de analisis de plagio: analisis 90 del trabajo 7 (TrabajoMock) iniciado por el docente del grupo (5).
 */
public final class PlagioMock {

    public static final int ID_ANALISIS = 90;
    public static final String SCAN_ID = "uagrm-0123456789abcdef0123";
    private static final Instant FECHA = Instant.parse("2026-11-17T14:00:00Z");

    private PlagioMock() {
    }

    /** Analisis EN_PROCESO del trabajo dado con un documento PDF (indice 1) pendiente. */
    public static AnalisisPlagio enProceso(Trabajo trabajo, TipoMotorPlagio motor) {
        AnalisisPlagio analisis = new AnalisisPlagio();
        analisis.setIdAnalisis(ID_ANALISIS);
        analisis.setTrabajo(trabajo);
        analisis.setEstado(EstadoAnalisisPlagio.EN_PROCESO);
        analisis.setMotor(motor);
        analisis.setUmbralMedio(30);
        analisis.setUmbralAlto(50);
        analisis.setComparacionInternet(true);
        analisis.setDocumentosAnalizados(1);
        analisis.setScanId(motor == TipoMotorPlagio.COPYLEAKS ? SCAN_ID : null);
        analisis.setUsuarioInicio(TrabajoMock.docente());
        analisis.setFechaInicio(FECHA);
        agregarDocumento(analisis, 1, "a".repeat(64));
        return analisis;
    }

    public static AnalisisPlagioDocumento agregarDocumento(AnalisisPlagio analisis, int indice, String sha256) {
        AnalisisPlagioDocumento doc = new AnalisisPlagioDocumento();
        doc.setIdAnalisisDocumento(900 + indice);
        doc.setAnalisis(analisis);
        doc.setIndice(indice);
        doc.setIdDocumento(70 + indice);
        doc.setNombreArchivo("documento-" + indice + ".pdf");
        doc.setRutaArchivo("trabajos/7/documento-" + indice + ".pdf");
        doc.setSha256(sha256);
        analisis.getDocumentos().add(doc);
        analisis.setDocumentosAnalizados(analisis.getDocumentos().size());
        return doc;
    }

    public static FuentePlagio fuente(AnalisisPlagio analisis, TipoFuentePlagio tipo, String titulo, String url,
                                      Integer idTrabajoFuente, String porcentaje) {
        FuentePlagio fuente = new FuentePlagio();
        fuente.setAnalisis(analisis);
        fuente.setTipo(tipo);
        fuente.setTitulo(titulo);
        fuente.setUrl(url);
        fuente.setIdTrabajoFuente(idTrabajoFuente);
        fuente.setPorcentaje(new BigDecimal(porcentaje));
        return fuente;
    }

    // ---------- Responses ----------

    public static AnalisisPlagioResponse enProcesoResponse() {
        return new AnalisisPlagioResponse(ID_ANALISIS, TrabajoMock.ID_TRABAJO, EstadoAnalisisPlagio.EN_PROCESO,
                TipoMotorPlagio.SIMULADO, null, null, 30, 50, false, List.of(), 2, null, "Juan Perez", FECHA, null);
    }

    public static AnalisisPlagioResponse completadoResponse() {
        return new AnalisisPlagioResponse(ID_ANALISIS, TrabajoMock.ID_TRABAJO, EstadoAnalisisPlagio.COMPLETADO,
                TipoMotorPlagio.COPYLEAKS, 34.2, NivelPlagio.MEDIO, 30, 50, true, List.of(
                new FuentePlagioResponse(TipoFuentePlagio.INTERNA, "Sistema de ventas", null, 12, 30.5),
                new FuentePlagioResponse(TipoFuentePlagio.INTERNA, "Trabajo no publicado del repositorio interno",
                        null, null, 20.0),
                new FuentePlagioResponse(TipoFuentePlagio.INTERNET, "Articulo", "https://example.org/a", null, 12.5)),
                2, null, "Juan Perez", FECHA, FECHA.plusSeconds(30));
    }

    public static AnalisisPlagioResponse errorResponse() {
        return new AnalisisPlagioResponse(ID_ANALISIS - 1, TrabajoMock.ID_TRABAJO, EstadoAnalisisPlagio.ERROR,
                TipoMotorPlagio.SIMULADO, null, null, 30, 50, false, List.of(), 1,
                "El analisis excedio el tiempo maximo.", "Juan Perez", FECHA.minusSeconds(7200),
                FECHA.minusSeconds(5400));
    }

    public static EstadoAnalisisPlagioResponse estado(EstadoAnalisisPlagio estado) {
        return estado == EstadoAnalisisPlagio.COMPLETADO
                ? new EstadoAnalisisPlagioResponse(ID_ANALISIS, estado, 34.2, NivelPlagio.ALTO)
                : new EstadoAnalisisPlagioResponse(ID_ANALISIS, estado, null, null);
    }

    /** Trabajo 7 en el estado dado (atajo). */
    public static Trabajo trabajo(EstadoTrabajo estado) {
        return TrabajoMock.trabajo(estado);
    }
}
