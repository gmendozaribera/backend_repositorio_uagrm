package com.uagrm.repositorio.plagio.copyleaks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.plagio.FuenteDetectada;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Webhook de Copyleaks: token (404), scan desconocido (404), completed, error, eventos sin cambios. */
class WebhookCopyleaksServiceTest {

    private static final String TOKEN = "w".repeat(40);
    private static final String SCAN = "uagrm-0123456789abcdef0123";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COMPLETED = """
            {"status":0,"scannedDocument":{"scanId":"uagrm-0123456789abcdef0123-2","totalWords":1000},
             "results":{
               "internet":[{"id":"i1","title":"Articulo","url":"https://example.org/a","matchedWords":125}],
               "repositories":[{"id":"r1","title":"Sistema de ventas","matchedWords":300,
                                "repositoryId":"repo","scanId":"uagrm-ffffffffffffffffffff-1"}],
               "database":[{"id":"d1","title":"Otro","matchedWords":50,"scanId":"ajeno"}],
               "score":{"identicalWords":300,"minorChangedWords":0,"relatedMeaningWords":0,"aggregatedScore":42.7}}}""";

    private final PlagioService plagioService = mock(PlagioService.class);
    private WebhookCopyleaksService service;

    @BeforeEach
    void setUp() {
        service = new WebhookCopyleaksService(plagioService, props(TOKEN));
        when(plagioService.analisisPorScan(SCAN)).thenReturn(Optional.of(90));
        when(plagioService.trabajoPorScan("uagrm-ffffffffffffffffffff")).thenReturn(Optional.of(12));
    }

    @Test
    void tokenIncorrectoOAusenteEs404SinTocarNada() {
        assertNoEncontrado(() -> service.procesar("x".repeat(40), "completed", SCAN + "-1", json("{}")));
        assertNoEncontrado(() -> service.procesar(TOKEN + "x", "completed", SCAN + "-1", json("{}")));
        WebhookCopyleaksService apagado = new WebhookCopyleaksService(plagioService, props(""));
        assertNoEncontrado(() -> apagado.procesar("", "completed", SCAN + "-1", json("{}")));
        verifyNoInteractions(plagioService);
    }

    @Test
    void scanDesconocidoOMalFormadoEs404() {
        when(plagioService.analisisPorScan("uagrm-otro")).thenReturn(Optional.empty());
        assertNoEncontrado(() -> service.procesar(TOKEN, "completed", "uagrm-otro-1", json(COMPLETED)));
        assertNoEncontrado(() -> service.procesar(TOKEN, "completed", "sinindice", json(COMPLETED)));
        assertNoEncontrado(() -> service.procesar(TOKEN, "completed", SCAN + "-x", json(COMPLETED)));
        verify(plagioService, never()).registrarDocumento(anyInt(), anyInt(), any(), any());
    }

    @Test
    void completedRegistraElDocumentoConPorcentajeYFuentes() {
        service.procesar(TOKEN, "completed", SCAN + "-2", json(COMPLETED));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FuenteDetectada>> fuentes = ArgumentCaptor.forClass(List.class);
        verify(plagioService).registrarDocumento(eq(90), eq(2), eq(new BigDecimal("42.7")), fuentes.capture());
        assertThat(fuentes.getValue()).containsExactly(
                new FuenteDetectada(TipoFuentePlagio.INTERNET, "Articulo", "https://example.org/a", null,
                        new BigDecimal("12.50")),
                new FuenteDetectada(TipoFuentePlagio.INTERNA, "Sistema de ventas", null, 12, new BigDecimal("30.00")),
                new FuenteDetectada(TipoFuentePlagio.INTERNA, "Otro", null, null, new BigDecimal("5.00")));
    }

    @Test
    void errorPasaElAnalisisAErrorYOtrosEventosNoCambianNada() {
        service.procesar(TOKEN, "error", SCAN + "-1", json("{\"error\":{\"code\":1,\"message\":\"Bad request\"}}"));
        verify(plagioService).fallar(90, PlagioService.MENSAJE_PROVEEDOR);

        service.procesar(TOKEN, "creditsChecked", SCAN + "-1", json("{}"));
        service.procesar(TOKEN, "indexed", SCAN + "-1", json("{}"));
        verify(plagioService, never()).registrarDocumento(anyInt(), anyInt(), any(), any());

        assertThatThrownBy(() -> service.procesar(TOKEN, "otro", SCAN + "-1", json("{}")))
                .isInstanceOf(NegocioException.class).extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void avisoRepetidoEsIdempotentePorqueElServiceLoIgnora() {
        // PlagioService.registrarDocumento ignora resultados repetidos o de analisis terminados (PlagioServiceTest):
        // el webhook solo delega y responde 200 las dos veces
        service.procesar(TOKEN, "completed", SCAN + "-2", json(COMPLETED));
        service.procesar(TOKEN, "completed", SCAN + "-2", json(COMPLETED));
        verify(plagioService, org.mockito.Mockito.times(2)).registrarDocumento(eq(90), eq(2), any(), any());
    }

    private static void assertNoEncontrado(Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static JsonNode json(String texto) {
        return JSON.readTree(texto);
    }

    private static PlagioProperties props(String token) {
        return new PlagioProperties(TipoMotorPlagio.COPYLEAKS,
                new PlagioProperties.Webhook("https://api.uagrm.edu.bo", token),
                new PlagioProperties.Copyleaks("plagio@uagrm.edu.bo", "key", true, "",
                        "https://id.copyleaks.com/v3/account/login/api", "https://api.copyleaks.com"));
    }
}
