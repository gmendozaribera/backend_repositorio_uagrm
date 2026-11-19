package com.uagrm.repositorio.work;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.dto.trabajo.CalificarRequest;
import com.uagrm.repositorio.dto.trabajo.EstadoAccesoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.LimitePublicoService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.documento.DescargaArchivo;
import com.uagrm.repositorio.imp.service.documento.DocumentoService;
import com.uagrm.repositorio.imp.service.trabajo.RepositorioService;
import com.uagrm.repositorio.imp.service.trabajo.TrabajoService;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import com.uagrm.repositorio.work.config.SecurityConfig;
import com.uagrm.repositorio.work.controller.DocumentoController;
import com.uagrm.repositorio.work.controller.PublicoController;
import com.uagrm.repositorio.work.controller.RepositorioController;
import com.uagrm.repositorio.work.controller.TrabajoController;
import com.uagrm.repositorio.mockup.SeguridadHttpMock;
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
@WebMvcTest(controllers = {TrabajoController.class, DocumentoController.class, RepositorioController.class,
        PublicoController.class})
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
    private RepositorioService repositorioService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;
    /** Limite por IP de /publico/** (su logica real esta en LimitePublicoFilterTest); por defecto deja pasar. */
    @MockitoBean
    private LimitePublicoService limitePublicoService;

    // ---------- Trabajos ----------

    @Test
    void crearTrabajo() throws Exception {
        when(trabajoService.crear(any(), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.BORRADOR, new AccionesResponse(true, true, true, false, false, false, false)));
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
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.tamano").exists()).andExpect(cumpleContratoSoloRespuesta());
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
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.id").exists()).andExpect(cumpleContratoSoloRespuesta());
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
        // @PathVariable @Positive + @Valid @RequestBody: el 400 tambien trae el mapa errores (campo del body)
        llamar(put("/api/v1/trabajos/7"), estudiante(), TRABAJO.replace("Sistema de inventarios", "Sis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos invalidos"))
                .andExpect(jsonPath("$.errores.titulo").exists())
                .andExpect(cumpleContratoSoloRespuesta());
        // id de ruta no numerico: mismo formato, con el nombre del parametro
        llamar(put("/api/v1/trabajos/abc"), estudiante(), TRABAJO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.id").exists())
                .andExpect(cumpleContratoSoloRespuesta());
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
                TrabajoMock.trabajoResponse(EstadoTrabajo.EN_REVISION, new AccionesResponse(false, false, false, false, true, true, false)));
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
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.motivo").exists()).andExpect(cumpleContratoSoloRespuesta());
        when(trabajoService.tomarRevision(any(), eq(7))).thenThrow(
                NegocioException.conflicto("No se puede tomar en revision un trabajo en estado BORRADOR."));
        llamar(patch("/api/v1/trabajos/7/revisar"), docente(), null).andExpect(status().isConflict()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/trabajos/7/aprobar"), estudiante(), "{}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        when(trabajoService.aprobar(any(), eq(9), any())).thenThrow(NegocioException.noEncontrado("No existe el trabajo 9."));
        llamar(patch("/api/v1/trabajos/9/aprobar"), docente(), "{}").andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    // ---------- Calificar (CU18) ----------

    @Test
    void calificarComoAdministradorDeCurso() throws Exception {
        when(trabajoService.calificar(any(), eq(7), any())).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.EVALUADO, AccionesResponse.NINGUNA));
        llamar(patch("/api/v1/trabajos/7/calificar"), docente(), "{\"calificacion\":85}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("EVALUADO"))
                .andExpect(jsonPath("$.data.calificacion").value(85))
                .andExpect(jsonPath("$.data.escalaCalifMax").value(100))
                .andExpect(jsonPath("$.data.acciones.calificar").value(false))
                .andExpect(cumpleContrato());
        verify(trabajoService).calificar(any(), eq(7), eq(new CalificarRequest(85)));
    }

    @Test
    void detalleNoEvaluadoConCalificacionNullYAccionCalificar() throws Exception {
        when(trabajoService.obtener(any(), eq(7))).thenReturn(TrabajoMock.trabajoResponse(EstadoTrabajo.APROBADO,
                new AccionesResponse(false, false, false, false, false, false, true)));
        llamar(get("/api/v1/trabajos/7"), docente(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calificacion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.acciones.calificar").value(true))
                .andExpect(cumpleContrato());
    }

    @Test
    void calificarErrores() throws Exception {
        // Bean Validation: falta o negativa
        llamar(patch("/api/v1/trabajos/7/calificar"), docente(), "{\"calificacion\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos invalidos"))
                .andExpect(jsonPath("$.errores.calificacion").exists()).andExpect(cumpleContratoSoloRespuesta());
        llamar(patch("/api/v1/trabajos/7/calificar"), docente(), "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.calificacion").exists()).andExpect(cumpleContratoSoloRespuesta());
        // Rango de la facultad (service)
        when(trabajoService.calificar(any(), eq(7), any())).thenThrow(
                NegocioException.campoInvalido("calificacion", "debe estar entre 0 y 100"));
        llamar(patch("/api/v1/trabajos/7/calificar"), docente(), "{\"calificacion\":101}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.calificacion").value("debe estar entre 0 y 100"))
                .andExpect(cumpleContrato());
        // Docente del grupo que no es Administrador de Curso
        when(trabajoService.calificar(any(), eq(8), any())).thenThrow(NegocioException.accesoDenegado(
                "Solo el Administrador de Curso del grupo puede calificar el trabajo."));
        llamar(patch("/api/v1/trabajos/8/calificar"), docente(), "{\"calificacion\":80}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        when(trabajoService.calificar(any(), eq(9), any())).thenThrow(NegocioException.noEncontrado("No existe el trabajo 9."));
        llamar(patch("/api/v1/trabajos/9/calificar"), docente(), "{\"calificacion\":80}")
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        when(trabajoService.calificar(any(), eq(10), any())).thenThrow(
                NegocioException.conflicto("No se puede calificar un trabajo en estado PENDIENTE."));
        llamar(patch("/api/v1/trabajos/10/calificar"), docente(), "{\"calificacion\":80}")
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
        // Por rol: estudiante y admin no llegan al service
        llamar(patch("/api/v1/trabajos/7/calificar"), estudiante(), "{\"calificacion\":80}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/trabajos/7/calificar"), admin(), "{\"calificacion\":80}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/trabajos/7/calificar"), null, "{\"calificacion\":80}")
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
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

    // ---------- Repositorio ----------

    @Test
    void buscarEnElRepositorioCualquierRol() throws Exception {
        when(repositorioService.buscar(any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.repositorioResumen()), 0, 20, 1, 1));
        for (String rol : new String[]{estudiante(), docente(), admin()}) {
            llamar(get("/api/v1/repositorio"), rol, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contenido[0].extracto").exists())
                    .andExpect(jsonPath("$.data.contenido[0].estado").doesNotExist())
                    .andExpect(cumpleContrato());
        }
        llamar(get("/api/v1/repositorio").param("q", "Diseño de software").param("hashtag", "springboot")
                .param("idGrupo", "41").param("idFacultad", "1").param("idCarrera", "2").param("idMateria", "20")
                .param("idSemestre", "30")
                .param("idTipoTrabajo", "60").param("pagina", "1").param("tamano", "5"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(repositorioService).buscar(eq(new FiltrosRepositorio("Diseño de software", "springboot", 41, 1, 2, 20, 30,
                60)), eq(1), eq(5));
    }

    @Test
    void buscarConParametrosInvalidosDevuelve400() throws Exception {
        llamar(get("/api/v1/repositorio").param("q", "x".repeat(101)), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(get("/api/v1/repositorio").param("idCarrera", "0"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(get("/api/v1/repositorio").param("idMateria", "abc"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(get("/api/v1/repositorio").param("tamano", "101"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void repositorioSinTokenOConPasswordPendiente() throws Exception {
        llamar(get("/api/v1/repositorio"), null, null).andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        llamar(get("/api/v1/repositorio/7"), null, null).andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        Usuario pendiente = UsuarioMock.usuario(41, "219012399", TipoUsuario.ESTUDIANTE);
        pendiente.setRequiereCambioPassword(true);
        llamar(get("/api/v1/repositorio"), token(pendiente), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/repositorio/7"), token(pendiente), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void fichaDelRepositorioY404() throws Exception {
        when(repositorioService.obtener(any(), eq(7))).thenReturn(TrabajoMock.publico());
        llamar(get("/api/v1/repositorio/7"), estudiante(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autores[0].esUsuarioActual").value(true))
                .andExpect(jsonPath("$.data.calificacion").doesNotExist())
                .andExpect(jsonPath("$.data.historial").doesNotExist())
                .andExpect(cumpleContrato());
        llamar(get("/api/v1/repositorio/7"), docente(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        when(repositorioService.obtener(any(), eq(8))).thenThrow(NegocioException.noEncontrado("No existe el trabajo 8."));
        llamar(get("/api/v1/repositorio/8"), estudiante(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/repositorio/0"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Publico (anonimo, security: []) ----------

    @Test
    void busquedaPublicaSinTokenYConTokenInvalido() throws Exception {
        when(repositorioService.buscarPublico(any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.repositorioResumen()), 0, 20, 1, 1));
        llamar(get("/api/v1/publico/repositorio"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contenido[0].calificacion").doesNotExist())
                .andExpect(cumpleContrato());
        // el token se ignora en las rutas publicas: uno vencido o falso no produce 401 ni consulta Redis
        llamar(get("/api/v1/publico/repositorio"), "Bearer no-es-un-jwt", null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/publico/repositorio").param("q", "\"sistema de informacion\" -redes")
                .param("hashtag", "springboot").param("idGrupo", "41").param("idFacultad", "1")
                .param("idCarrera", "2").param("idMateria", "20").param("idSemestre", "30")
                .param("idTipoTrabajo", "60").param("pagina", "1").param("tamano", "5"), null, null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(repositorioService).buscarPublico(eq(new FiltrosRepositorio("\"sistema de informacion\" -redes",
                "springboot", 41, 1, 2, 20, 30, 60)), eq(1), eq(5));
        org.mockito.Mockito.verifyNoInteractions(revocacionTokenService);
    }

    @Test
    void busquedaPublicaCerradaOInvalida() throws Exception {
        when(repositorioService.buscarPublico(any(), anyInt(), anyInt())).thenThrow(NegocioException.accesoDenegado(
                "El repositorio publico no esta habilitado: inicie sesion para consultar los trabajos."));
        llamar(get("/api/v1/publico/repositorio"), null, null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/publico/repositorio").param("tamano", "101"), null, null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(get("/api/v1/publico/repositorio").param("idGrupo", "0"), null, null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void fichaPublicaSinToken() throws Exception {
        when(repositorioService.obtenerPublico(7)).thenReturn(TrabajoMock.publico());
        llamar(get("/api/v1/publico/repositorio/7"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calificacion").doesNotExist())
                .andExpect(jsonPath("$.data.historial").doesNotExist())
                .andExpect(cumpleContrato());
        when(repositorioService.obtenerPublico(8)).thenThrow(NegocioException.noEncontrado("No existe el trabajo 8."));
        llamar(get("/api/v1/publico/repositorio/8"), null, null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(get("/api/v1/publico/repositorio/0"), null, null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void estadoDelAccesoPublico() throws Exception {
        when(repositorioService.estadoPublico()).thenReturn(new EstadoAccesoPublicoResponse(true, List.of(1)));
        llamar(get("/api/v1/publico/estado"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.habilitado").value(true))
                .andExpect(jsonPath("$.data.facultades[0]").value(1))
                .andExpect(cumpleContrato());
        when(repositorioService.estadoPublico()).thenReturn(new EstadoAccesoPublicoResponse(false, List.of()));
        llamar(get("/api/v1/publico/estado"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.habilitado").value(false))
                .andExpect(cumpleContrato());
    }

    @Test
    void demasiadasConsultasPublicasDevuelven429ConRetryAfter() throws Exception {
        doThrow(new LimiteIntentosException(42, "Demasiadas consultas",
                "Supero el limite de 60 consultas por minuto al repositorio publico. Intente de nuevo en 42 segundos."))
                .when(limitePublicoService).registrar(any());
        for (String ruta : List.of("/api/v1/publico/repositorio", "/api/v1/publico/repositorio/7", "/api/v1/publico/estado")) {
            llamar(get(ruta), null, null)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "42"))
                    .andExpect(jsonPath("$.detail").value(containsString("60 consultas por minuto")))
                    .andExpect(cumpleContrato());
        }
        verifyNoInteractions(repositorioService);
    }

    @Test
    void rutasAutenticadasNoPasanPorElLimitePublico() throws Exception {
        when(repositorioService.buscar(any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.repositorioResumen()), 0, 20, 1, 1));
        when(trabajoService.obtener(any(), eq(7))).thenReturn(
                TrabajoMock.trabajoResponse(EstadoTrabajo.RECHAZADO, AccionesResponse.NINGUNA));
        llamar(get("/api/v1/repositorio"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/trabajos/7"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        verifyNoInteractions(limitePublicoService);
        llamar(get("/api/v1/publico/estado"), null, null);
        verify(limitePublicoService).registrar("127.0.0.1");
    }

    @Test
    void anonimoNoDescargaNiEscribeEnRutasPublicas() throws Exception {
        llamar(get("/api/v1/documentos/70/descarga"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
        // solo GET es publico: cualquier otro metodo queda denegado
        mvc.perform(post("/api/v1/publico/repositorio")).andExpect(status().isUnauthorized());
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

    // ---------- hardening HTTP (S5): cabeceras de seguridad, CORS y 500 sin detalles ----------

    @Test
    void cabecerasDeSeguridadEnRespuestas200y401() throws Exception {
        // La descarga de un archivo privado tambien es no-store, y el frontend puede leer Content-Disposition
        byte[] pdf = "%PDF-1.4 contenido".getBytes(StandardCharsets.US_ASCII);
        when(documentoService.descargar(any(), eq(70))).thenReturn(new DescargaArchivo("final.pdf",
                "application/pdf", pdf.length, new ByteArrayInputStream(pdf)));
        mvc.perform(get("/api/v1/documentos/70/descarga").header("Authorization", docente())
                        .header("Origin", SeguridadHttpMock.ORIGEN_PERMITIDO).accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(SeguridadHttpMock.corsExponeSoloLoNecesario())
                .andExpect(SeguridadHttpMock.cabecerasApi());
        llamar(get("/api/v1/trabajos/mios"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(SeguridadHttpMock.cabecerasApi());
    }

    @Test
    void preflightCorsSoloDesdeElOrigenDelFrontend() throws Exception {
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/trabajos/7", SeguridadHttpMock.ORIGEN_PERMITIDO, "PUT"))
                .andExpect(SeguridadHttpMock.preflightPermitido());
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/trabajos/7", SeguridadHttpMock.ORIGEN_AJENO, "PUT"))
                .andExpect(SeguridadHttpMock.preflightRechazado());
    }

    @Test
    void errorInesperadoNoExponeMensajesNiTrazas() throws Exception {
        when(documentoService.descargar(any(), eq(72))).thenThrow(new IllegalStateException(SeguridadHttpMock.MENSAJE_INTERNO));
        llamar(get("/api/v1/documentos/72/descarga"), docente(), null)
                .andExpect(SeguridadHttpMock.error500SinDetallesInternos());
    }
}
