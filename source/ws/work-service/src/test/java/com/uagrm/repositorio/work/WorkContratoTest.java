package com.uagrm.repositorio.work;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.documento.DescargaArchivo;
import com.uagrm.repositorio.imp.service.documento.DocumentoService;
import com.uagrm.repositorio.imp.service.trabajo.TrabajoService;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.work.config.SecurityConfig;
import com.uagrm.repositorio.work.controller.DocumentoController;
import com.uagrm.repositorio.work.controller.TrabajoController;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Test de contrato: los requests y responses reales de work-service (serializacion Jackson, seguridad por rol,
 * ProblemDetail) cumplen spec/openapi/work-api.yaml. Cubre todas las operaciones. Los servicios estan mockeados:
 * aqui se prueba la forma de la API, no la logica de negocio (eso esta en los tests de imp).
 *
 * <p>Limitaciones documentadas: (1) subida multipart: el validador no ve las partes de MockMvc, se ignora solo
 * {@code validation.request.body.missing} (igual que UserContratoTest) y los campos de texto del formulario que el
 * adaptador presenta como query; (2) descarga binaria: el contrato declara
 * application/octet-stream y la respuesta real lleva el tipo del archivo, asi que se verifican status y cabeceras
 * (Content-Disposition, Content-Type, nosniff) sin el validador.</p>
 */
@WebMvcTest(controllers = {TrabajoController.class, DocumentoController.class})
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
class WorkContratoTest {

    private static final String CONTRATO = "openapi/work-api.yaml";

    private static final OpenApiInteractionValidator ESTRICTO =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO).build();

    private static final OpenApiInteractionValidator SOLO_RESPUESTA =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    /**
     * Subida multipart: el adaptador de MockMvc no ve las partes (reporta "body missing") y presenta los campos de
     * texto del formulario (titulo, categoria, idFase) como parametros de query ("unexpected"). Se ignoran solo esas
     * dos reglas; ruta, metodo, seguridad y respuesta si se validan.
     */
    private static final OpenApiInteractionValidator MULTIPART =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request.body.missing", ValidationReport.Level.IGNORE)
                            .withLevel("validation.request.parameter.query.unexpected", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    private static final String TRABAJO = """
            {"titulo":"Sistema de inventarios","resumen":"Analisis, diseno e implementacion de un sistema.",
             "idGrupo":40,"idTipoTrabajo":60,"hashtags":["springboot","inventarios"],"registrosCoautores":[219012346]}""";
    private static final String ENLACE = """
            {"titulo":"Repositorio del proyecto","descripcion":"Codigo fuente","url":"https://github.com/usuario/proyecto",
             "categoria":"CODIGO","idFase":50}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TrabajoService trabajoService;
    @MockitoBean
    private DocumentoService documentoService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    // ---------- Trabajos ----------

    @Test
    void crearTrabajo() throws Exception {
        when(trabajoService.crear(any(), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.BORRADOR, new AccionesResponse(true, true, true, false, false, false)));
        llamar(post("/api/v1/trabajos"), estudiante(), TRABAJO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.autores[0].esUsuarioActual").value(true))
                .andExpect(cumpleContrato());
        llamar(post("/api/v1/trabajos"), estudiante(), TRABAJO.replace(",\"hashtags\":[\"springboot\",\"inventarios\"],"
                + "\"registrosCoautores\":[219012346]", "")).andExpect(status().isCreated()).andExpect(cumpleContrato());
    }

    @Test
    void crearConDatosInvalidosDevuelve400ConErrores() throws Exception {
        llamar(post("/api/v1/trabajos"), estudiante(), TRABAJO.replace("Sistema de inventarios", "Sis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.titulo").exists())
                .andExpect(cumpleContratoSoloRespuesta());
        when(trabajoService.crear(any(), any())).thenThrow(
                NegocioException.campoInvalido("registrosCoautores", "no corresponden a estudiantes activos: 219012346"));
        llamar(post("/api/v1/trabajos"), estudiante(), TRABAJO).andExpect(status().isBadRequest()).andExpect(cumpleContrato());
    }

    @Test
    void crearComoDocenteDevuelve403YGrupoInactivo409() throws Exception {
        llamar(post("/api/v1/trabajos"), docente(), TRABAJO).andExpect(status().isForbidden()).andExpect(cumpleContrato());
        when(trabajoService.crear(any(), any())).thenThrow(NegocioException.conflicto("El grupo SA esta inactivo."));
        llamar(post("/api/v1/trabajos"), estudiante(), TRABAJO).andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void misTrabajosConFiltroYPaginacion() throws Exception {
        when(trabajoService.listarMios(any(), any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.resumen(EstadoTrabajo.BORRADOR), TrabajoMock.resumen(EstadoTrabajo.PENDIENTE)),
                0, 20, 2, 1));
        llamar(get("/api/v1/trabajos/mios"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/trabajos/mios").param("estado", "RECHAZADO").param("pagina", "1").param("tamano", "5"),
                estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(trabajoService).listarMios(any(), eq(EstadoTrabajo.RECHAZADO), eq(1), eq(5));

        llamar(get("/api/v1/trabajos/mios"), docente(), null).andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/trabajos/mios").param("tamano", "101"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void bandejaDeRevision() throws Exception {
        when(trabajoService.listarRevision(any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PaginaResponse<>(List.of(TrabajoMock.resumen(EstadoTrabajo.PENDIENTE)), 0, 20, 1, 1));
        llamar(get("/api/v1/trabajos/revision"), docente(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/trabajos/revision").param("estado", "EN_REVISION").param("idGrupo", "40"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(trabajoService).listarRevision(any(), eq(EstadoTrabajo.EN_REVISION), eq(40), eq(0), eq(20));
        llamar(get("/api/v1/trabajos/revision"), estudiante(), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void obtenerTrabajoY404() throws Exception {
        when(trabajoService.obtener(any(), eq(7))).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.RECHAZADO, AccionesResponse.NINGUNA));
        llamar(get("/api/v1/trabajos/7"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        when(trabajoService.obtener(any(), eq(99))).thenThrow(NegocioException.noEncontrado("No existe el trabajo 99."));
        llamar(get("/api/v1/trabajos/99"), estudiante(), null).andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/trabajos/0"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void actualizarYEliminar() throws Exception {
        when(trabajoService.actualizar(any(), eq(7), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.BORRADOR, AccionesResponse.NINGUNA));
        llamar(put("/api/v1/trabajos/7"), estudiante(), TRABAJO).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/trabajos/7"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(trabajoService).eliminar(any(), eq(7));

        when(trabajoService.actualizar(any(), eq(8), any())).thenThrow(
                NegocioException.conflicto("El trabajo esta en estado PENDIENTE y no se puede modificar."));
        llamar(put("/api/v1/trabajos/8"), estudiante(), TRABAJO).andExpect(status().isConflict()).andExpect(cumpleContrato());
        doThrow(NegocioException.conflicto("Solo se puede eliminar un trabajo en estado BORRADOR."))
                .when(trabajoService).eliminar(any(), eq(8));
        llamar(delete("/api/v1/trabajos/8"), estudiante(), null).andExpect(status().isConflict()).andExpect(cumpleContrato());
        llamar(put("/api/v1/trabajos/7"), docente(), TRABAJO).andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void enviarARevision() throws Exception {
        when(trabajoService.enviar(any(), eq(7))).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.PENDIENTE, AccionesResponse.NINGUNA));
        llamar(patch("/api/v1/trabajos/7/enviar"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        when(trabajoService.enviar(any(), eq(8))).thenThrow(
                NegocioException.datosInvalidos("Para enviar el trabajo debe subir al menos un documento PDF."));
        llamar(patch("/api/v1/trabajos/8/enviar"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContrato());
    }

    // ---------- Revision ----------

    @Test
    void tomarRevisionAprobarYRechazar() throws Exception {
        when(trabajoService.tomarRevision(any(), eq(7))).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.EN_REVISION, new AccionesResponse(false, false, false, false, true, true)));
        llamar(patch("/api/v1/trabajos/7/revisar"), docente(), null).andExpect(status().isOk()).andExpect(cumpleContrato());

        when(trabajoService.aprobar(any(), eq(7), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.APROBADO_CON_OBSERVACIONES, AccionesResponse.NINGUNA));
        llamar(patch("/api/v1/trabajos/7/aprobar"), docente(), "{\"observaciones\":\"Corregir la bibliografia.\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/trabajos/7/aprobar"), docente(), "{\"observaciones\":null}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());

        when(trabajoService.rechazar(any(), eq(7), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.RECHAZADO, AccionesResponse.NINGUNA));
        llamar(patch("/api/v1/trabajos/7/rechazar"), docente(), "{\"motivo\":\"No corresponde al tema asignado.\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void revisionInvalida() throws Exception {
        llamar(patch("/api/v1/trabajos/7/rechazar"), docente(), "{\"motivo\":\"corto\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        when(trabajoService.tomarRevision(any(), eq(7))).thenThrow(
                NegocioException.conflicto("No se puede tomar en revision un trabajo en estado BORRADOR."));
        llamar(patch("/api/v1/trabajos/7/revisar"), docente(), null).andExpect(status().isConflict()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/trabajos/7/aprobar"), estudiante(), "{}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        when(trabajoService.aprobar(any(), eq(9), any())).thenThrow(NegocioException.noEncontrado("No existe el trabajo 9."));
        llamar(patch("/api/v1/trabajos/9/aprobar"), docente(), "{}").andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    // ---------- Documentos ----------

    @Test
    void subirDocumento() throws Exception {
        when(documentoService.subir(any(), eq(7), any(), eq("Documento final"), isNull(), eq(CategoriaDocumento.DOCUMENTO),
                eq(50))).thenReturn(TrabajoMock.documentoResponse());
        llamar(subida(7), estudiante(), null).andExpect(status().isCreated()).andExpect(cumpleContratoMultipart());
    }

    @Test
    void subidaRechazada() throws Exception {
        when(documentoService.subir(any(), eq(7), any(), any(), any(), any(), any())).thenThrow(
                NegocioException.campoInvalido("archivo", "supera el tamano maximo de 10 MB",
                        "El archivo supera el tamano maximo de 10 MB de la facultad."));
        llamar(subida(7), estudiante(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.archivo").value("supera el tamano maximo de 10 MB"))
                .andExpect(cumpleContratoMultipart());

        when(documentoService.subir(any(), eq(8), any(), any(), any(), any(), any())).thenThrow(
                new LimiteIntentosException(42, "Demasiadas subidas", "Supero el limite de 10 subidas por minuto."));
        llamar(subida(8), estudiante(), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(cumpleContratoMultipart());

        when(documentoService.subir(any(), eq(9), any(), any(), any(), any(), any())).thenThrow(
                NegocioException.noDisponible("El almacenamiento de archivos no esta disponible."));
        llamar(subida(9), estudiante(), null)
                .andExpect(status().isServiceUnavailable()).andExpect(cumpleContratoMultipart());

        llamar(subida(7), docente(), null).andExpect(status().isForbidden()).andExpect(cumpleContratoMultipart());
    }

    @Test
    void agregarEnlace() throws Exception {
        when(documentoService.agregarEnlace(any(), eq(7), any())).thenReturn(TrabajoMock.enlaceResponse());
        llamar(post("/api/v1/trabajos/7/enlaces"), estudiante(), ENLACE).andExpect(status().isCreated())
                .andExpect(cumpleContrato());
        when(documentoService.agregarEnlace(any(), eq(8), any())).thenThrow(
                NegocioException.campoInvalido("url", "el sitio no esta permitido para CODIGO"));
        llamar(post("/api/v1/trabajos/8/enlaces"), estudiante(), ENLACE).andExpect(status().isBadRequest())
                .andExpect(cumpleContrato());
    }

    @Test
    void eliminarDocumento() throws Exception {
        llamar(delete("/api/v1/documentos/70"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(documentoService).eliminar(any(), eq(70));
        doThrow(NegocioException.conflicto("El trabajo esta en estado PENDIENTE y no se puede modificar."))
                .when(documentoService).eliminar(any(), eq(71));
        llamar(delete("/api/v1/documentos/71"), estudiante(), null).andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void descargarDocumento() throws Exception {
        byte[] pdf = "%PDF-1.4 contenido".getBytes(StandardCharsets.US_ASCII);
        when(documentoService.descargar(any(), eq(70))).thenReturn(new DescargaArchivo("diseño final.pdf",
                "application/pdf", pdf.length, new ByteArrayInputStream(pdf)));
        mvc.perform(get("/api/v1/documentos/70/descarga").header("Authorization", docente()).accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"dise_o final.pdf\"; filename*=UTF-8''dise%C3%B1o%20final.pdf"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Length", String.valueOf(pdf.length)))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void descargarEnlaceONoVisible() throws Exception {
        when(documentoService.descargar(any(), eq(71))).thenThrow(
                NegocioException.conflicto("El documento 71 es un enlace externo: se abre directamente su urlExterno."));
        llamar(get("/api/v1/documentos/71/descarga"), estudiante(), null)
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
        when(documentoService.descargar(any(), eq(70))).thenThrow(NegocioException.noEncontrado("No existe el documento 70."));
        llamar(get("/api/v1/documentos/70/descarga"), estudiante(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void reglasDeDocumentos() throws Exception {
        when(documentoService.reglas(1)).thenReturn(TrabajoMock.reglas());
        llamar(get("/api/v1/documentos/reglas").param("idFacultad", "1"), estudiante(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAutores").value(5))
                .andExpect(cumpleContrato());
        when(documentoService.reglas(99)).thenThrow(NegocioException.noEncontrado("No existe la facultad 99."));
        llamar(get("/api/v1/documentos/reglas").param("idFacultad", "99"), docente(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/documentos/reglas"), docente(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenDevuelve401YRedisCaido503() throws Exception {
        llamar(get("/api/v1/trabajos/7"), null, null).andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        llamar(get("/api/v1/documentos/70/descarga"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        when(revocacionTokenService.estaRevocado(any())).thenThrow(new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio no disponible", "El servicio de sesiones no esta disponible."));
        llamar(get("/api/v1/trabajos/7"), estudiante(), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("sesiones")))
                .andExpect(cumpleContrato());
    }

    // ---------- helpers ----------

    private AbstractMockHttpServletRequestBuilder<?> subida(int idTrabajo) {
        return multipart("/api/v1/trabajos/{id}/documentos", idTrabajo)
                .file(new MockMultipartFile("archivo", "informe-final.pdf", "application/pdf",
                        "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)))
                .param("titulo", "Documento final")
                .param("categoria", "DOCUMENTO")
                .param("idFase", "50");
    }

    private ResultActions llamar(AbstractMockHttpServletRequestBuilder<?> request, String bearer, String cuerpo) throws Exception {
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        if (cuerpo != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
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

    private static ResultMatcher cumpleContratoMultipart() {
        return OpenApiValidationMatchers.openApi().isValid(MULTIPART);
    }

    private static ResultMatcher cumpleContratoSoloRespuesta() {
        return OpenApiValidationMatchers.openApi().isValid(SOLO_RESPUESTA);
    }
}
