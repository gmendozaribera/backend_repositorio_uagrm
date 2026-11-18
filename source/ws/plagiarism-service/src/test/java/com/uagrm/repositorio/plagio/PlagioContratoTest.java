package com.uagrm.repositorio.plagio;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.mockup.PlagioMock;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.plagio.config.SecurityConfig;
import com.uagrm.repositorio.plagio.controller.PlagioController;
import com.uagrm.repositorio.plagio.controller.WebhookCopyleaksController;
import com.uagrm.repositorio.plagio.copyleaks.WebhookCopyleaksService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Test de contrato: los requests y responses reales de plagiarism-service (serializacion Jackson, seguridad por
 * rol, ProblemDetail) cumplen spec/openapi/plagio-api.yaml. Cubre todas las operaciones, incluido el webhook
 * ({@code security: []}). Los servicios estan mockeados: la logica esta en PlagioServiceTest y
 * WebhookCopyleaksServiceTest.
 */
@WebMvcTest(controllers = {PlagioController.class, WebhookCopyleaksController.class})
@Import({SecurityConfig.class, SeguridadConfig.class, SeguridadErrorHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.seguridad.jwt.emisor=repositorio-uagrm",
        "app.seguridad.jwt.refresh-ttl=7d",
        "app.seguridad.dos-factores.emisor=Repositorio UAGRM",
        "app.seguridad.dos-factores.ttl-pendiente=300s",
        "app.seguridad.dos-factores.max-intentos=5",
        "app.seguridad.cors-origenes=http://localhost:4200"
})
class PlagioContratoTest {

    private static final String CONTRATO = "openapi/plagio-api.yaml";
    private static final String TOKEN = "t".repeat(40);
    private static final String WEBHOOK = "/api/v1/plagio/webhook/copyleaks/{token}/{evento}/{scanId}";
    private static final String COMPLETED = """
            {"status":0,"scannedDocument":{"scanId":"uagrm-0123456789abcdef0123-1","totalWords":1200},
             "results":{"internet":[],"score":{"aggregatedScore":16.5}}}""";

    private static final OpenApiInteractionValidator ESTRICTO =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO).build();

    private static final OpenApiInteractionValidator SOLO_RESPUESTA =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PlagioService plagioService;
    @MockitoBean
    private WebhookCopyleaksService webhookService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    // ---------- Iniciar ----------

    @Test
    void iniciarResponde202EnProceso() throws Exception {
        when(plagioService.iniciar(any(), eq(7))).thenReturn(PlagioMock.enProcesoResponse());
        llamar(post("/api/v1/plagio/trabajos/7"), docente())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.data.porcentajeSimilitud").isEmpty())
                .andExpect(cumpleContrato());
        llamar(post("/api/v1/plagio/trabajos/7"), admin()).andExpect(status().isAccepted()).andExpect(cumpleContrato());
    }

    @Test
    void iniciarErrores() throws Exception {
        llamar(post("/api/v1/plagio/trabajos/7"), estudiante())
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        verifyNoInteractions(plagioService);

        when(plagioService.iniciar(any(), eq(8))).thenThrow(NegocioException.conflicto(
                "Ya hay un analisis de plagio en curso para este trabajo."));
        llamar(post("/api/v1/plagio/trabajos/8"), docente()).andExpect(status().isConflict()).andExpect(cumpleContrato());
        when(plagioService.iniciar(any(), eq(9))).thenThrow(NegocioException.datosInvalidos(
                "El trabajo no tiene archivos analizables: se analizan documentos PDF, DOCX u ODT."));
        llamar(post("/api/v1/plagio/trabajos/9"), docente()).andExpect(status().isBadRequest()).andExpect(cumpleContrato());
        when(plagioService.iniciar(any(), eq(10))).thenThrow(NegocioException.noEncontrado("No existe el trabajo 10."));
        llamar(post("/api/v1/plagio/trabajos/10"), docente()).andExpect(status().isNotFound()).andExpect(cumpleContrato());
        when(plagioService.iniciar(any(), eq(11))).thenThrow(NegocioException.noDisponible(
                "La deteccion de plagio no esta disponible en este momento."));
        llamar(post("/api/v1/plagio/trabajos/11"), docente())
                .andExpect(status().isServiceUnavailable()).andExpect(cumpleContrato());
        llamar(post("/api/v1/plagio/trabajos/0"), docente())
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Lectura ----------

    @Test
    void ultimoAnalisisCompletadoConFuentes() throws Exception {
        when(plagioService.ultimo(any(), eq(7))).thenReturn(PlagioMock.completadoResponse());
        llamar(get("/api/v1/plagio/trabajos/7"), docente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nivel").value("MEDIO"))
                .andExpect(jsonPath("$.data.fuentes[1].idTrabajo").isEmpty())
                .andExpect(cumpleContrato());
        when(plagioService.ultimo(any(), eq(8))).thenThrow(NegocioException.noEncontrado(
                "El trabajo 8 no tiene analisis de plagio."));
        llamar(get("/api/v1/plagio/trabajos/8"), admin()).andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/plagio/trabajos/7"), estudiante())
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void estadoParaElPolling() throws Exception {
        when(plagioService.estado(any(), eq(7))).thenReturn(PlagioMock.estado(EstadoAnalisisPlagio.EN_PROCESO));
        llamar(get("/api/v1/plagio/trabajos/7/estado"), docente())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nivel").isEmpty()).andExpect(cumpleContrato());
        when(plagioService.estado(any(), eq(7))).thenReturn(PlagioMock.estado(EstadoAnalisisPlagio.COMPLETADO));
        llamar(get("/api/v1/plagio/trabajos/7/estado"), docente())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nivel").value("ALTO")).andExpect(cumpleContrato());
        when(plagioService.estado(any(), eq(8))).thenThrow(NegocioException.noEncontrado(
                "El trabajo 8 no tiene analisis de plagio."));
        llamar(get("/api/v1/plagio/trabajos/8/estado"), docente())
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/plagio/trabajos/7/estado"), estudiante())
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void historial() throws Exception {
        when(plagioService.historial(any(), eq(7))).thenReturn(List.of(PlagioMock.completadoResponse(),
                PlagioMock.errorResponse()));
        llamar(get("/api/v1/plagio/trabajos/7/historial"), admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].mensajeError").value("El analisis excedio el tiempo maximo."))
                .andExpect(cumpleContrato());
        when(plagioService.historial(any(), eq(8))).thenReturn(List.of());
        llamar(get("/api/v1/plagio/trabajos/8/historial"), docente()).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/plagio/trabajos/7/historial"), estudiante())
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    // ---------- Webhook (security: []) ----------

    @Test
    void webhookSinSesionResponde200() throws Exception {
        llamarWebhook("completed", COMPLETED, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(cumpleContrato());
        verify(webhookService).procesar(eq(TOKEN), eq("completed"), eq("uagrm-0123456789abcdef0123-1"), any());
        // un Authorization basura se ignora (no hay 401 ni consulta a Redis)
        llamarWebhook("error", "{\"error\":{\"code\":1,\"message\":\"Bad request\"}}", "Bearer vencido")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verifyNoInteractions(revocacionTokenService);
    }

    @Test
    void webhookTokenIncorrectoOScanDesconocido404() throws Exception {
        doThrow(NegocioException.noEncontrado("No encontrado.")).when(webhookService)
                .procesar(anyString(), anyString(), anyString(), any());
        llamarWebhook("completed", COMPLETED, null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void webhookSoloPost() throws Exception {
        mvc.perform(put(WEBHOOK, TOKEN, "completed", "x-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenDevuelve401YRedisCaido503() throws Exception {
        llamar(get("/api/v1/plagio/trabajos/7"), null).andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        when(revocacionTokenService.estaRevocado(any())).thenThrow(new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio no disponible", "El servicio de sesiones no esta disponible."));
        llamar(get("/api/v1/plagio/trabajos/7/estado"), docente())
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("sesiones")))
                .andExpect(cumpleContrato());
    }

    // ---------- helpers ----------

    private ResultActions llamarWebhook(String evento, String cuerpo, String authorization) throws Exception {
        AbstractMockHttpServletRequestBuilder<?> request = post(WEBHOOK, TOKEN, evento, "uagrm-0123456789abcdef0123-1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions llamar(AbstractMockHttpServletRequestBuilder<?> request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
    }

    private String estudiante() {
        return token(TrabajoMock.autor());
    }

    private String docente() {
        return token(TrabajoMock.docente());
    }

    private String admin() {
        return token(TrabajoMock.admin());
    }

    /** Access token real (firmado con el JwtService real) para pasar por el resource server real. */
    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generar(usuario, Duration.ofMinutes(5)).accessToken();
    }

    private static ResultMatcher cumpleContrato() {
        return OpenApiValidationMatchers.openApi().isValid(ESTRICTO);
    }

    private static ResultMatcher cumpleContratoSoloRespuesta() {
        return OpenApiValidationMatchers.openApi().isValid(SOLO_RESPUESTA);
    }
}
