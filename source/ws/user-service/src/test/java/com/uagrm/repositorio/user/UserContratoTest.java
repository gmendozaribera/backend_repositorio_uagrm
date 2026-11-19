package com.uagrm.repositorio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import com.uagrm.repositorio.dto.notificacion.NotificacionesPendientesResponse;
import com.uagrm.repositorio.dto.perfil.Preferencias;
import com.uagrm.repositorio.dto.padron.EstudianteHabilitadoResponse;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.correo.CorreoService;
import com.uagrm.repositorio.imp.service.dominio.DominioService;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.padron.PadronService;
import com.uagrm.repositorio.imp.service.perfil.PerfilService;
import com.uagrm.repositorio.imp.service.usuario.UsuarioService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.DominioMock;
import com.uagrm.repositorio.mockup.NotificacionMock;
import com.uagrm.repositorio.mockup.PadronMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import com.uagrm.repositorio.user.config.SecurityConfig;
import com.uagrm.repositorio.user.controller.ConfigSmtpController;
import com.uagrm.repositorio.user.controller.DominioController;
import com.uagrm.repositorio.user.controller.NotificacionController;
import com.uagrm.repositorio.user.controller.PadronController;
import com.uagrm.repositorio.user.controller.PerfilController;
import com.uagrm.repositorio.user.controller.TipoUsuarioController;
import com.uagrm.repositorio.user.controller.UsuarioController;
import com.uagrm.repositorio.mockup.SeguridadHttpMock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test de contrato: verifica que los requests y responses reales del user-service (serializacion Jackson,
 * seguridad solo ADMIN salvo notificaciones y perfil, ProblemDetail) cumplen spec/openapi/user-api.yaml. Cubre todas las operaciones.
 * Los servicios estan mockeados: aqui se prueba la forma de la API, no la logica de negocio.
 */
@WebMvcTest(controllers = {UsuarioController.class, TipoUsuarioController.class, PadronController.class,
        DominioController.class, ConfigSmtpController.class, NotificacionController.class, PerfilController.class})
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
class UserContratoTest {

    private static final String CONTRATO = "openapi/user-api.yaml";
    private static final int ID_ESTUDIANTE = 33;

    /** Valida request y response; rechaza propiedades que el contrato no declara. */
    private static final OpenApiInteractionValidator ESTRICTO =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO).build();

    /** Para los casos que envian a proposito un request invalido: solo se valida la respuesta de error. */
    private static final OpenApiInteractionValidator SOLO_RESPUESTA =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    /**
     * Importacion multipart: openapi-request-validator no ve las partes de un request multipart de MockMvc
     * (reporta "body missing"). Se ignora solo esa regla: ruta, metodo, seguridad y respuesta si se validan.
     */
    private static final OpenApiInteractionValidator MULTIPART =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request.body.missing", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    private static final String CREAR_DOCENTE = """
            {"usuario":"doc.maria","passwordInicial":"Inicial2026","rol":"DOCENTE","nombre":"Maria",
             "apellidos":"Lopez Suarez","ci":"5554443","correo":"maria.lopez@uagrm.edu.bo","celular":"+59170012345",
             "fechaNac":"1990-05-14"}""";
    private static final String CREAR_ESTUDIANTE = """
            {"usuario":"219012345","passwordInicial":"Inicial2026","rol":"ESTUDIANTE","nombre":"Juan Carlos",
             "apellidos":"Perez Lopez","ci":"7654321-1A","correo":"juan.perez@uagrm.edu.bo","celular":null,
             "fechaNac":null,"registro":219012345,"idCarrera":10}""";
    private static final String ACTUALIZAR = """
            {"rol":"DOCENTE","nombre":"Maria","apellidos":"Lopez Suarez","ci":"5554443",
             "correo":"maria.lopez@uagrm.edu.bo","celular":null,"fechaNac":null,"registro":null,"idCarrera":null,
             "estado":1}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private PadronService padronService;
    @MockitoBean
    private DominioService dominioService;
    @MockitoBean
    private CorreoService correoService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;
    @MockitoBean
    private NotificacionService notificacionService;
    @MockitoBean
    private PerfilService perfilService;

    // ---------- Usuarios ----------

    @Test
    void listarUsuariosConFiltrosYPaginado() throws Exception {
        when(usuarioService.listar(any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(
                new PaginaResponse<>(List.of(PadronMock.docenteResponse(), PadronMock.estudianteResponse()), 0, 20, 2, 1));

        llamar(get("/api/v1/usuarios"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/usuarios").param("q", "maria").param("rol", "DOCENTE").param("idCarrera", "10")
                .param("incluirInactivos", "true").param("pagina", "1").param("tamano", "50"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(usuarioService).listar("maria", "DOCENTE", 10, true, 1, 50);
    }

    @Test
    void paginacionFueraDeRangoDevuelve400() throws Exception {
        llamar(get("/api/v1/usuarios").param("tamano", "101"), admin(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(get("/api/v1/padron").param("pagina", "-1"), admin(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void crearUsuarios() throws Exception {
        when(usuarioService.crear(any())).thenReturn(PadronMock.docenteResponse());
        llamar(post("/api/v1/usuarios"), admin(), CREAR_DOCENTE)
                .andExpect(status().isCreated()).andExpect(cumpleContrato());

        when(usuarioService.crear(any())).thenReturn(PadronMock.estudianteResponse());
        llamar(post("/api/v1/usuarios"), admin(), CREAR_ESTUDIANTE)
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
    }

    @Test
    void crearConPasswordDebilDevuelveErroresPorCampo() throws Exception {
        llamar(post("/api/v1/usuarios"), admin(), CREAR_DOCENTE.replace("Inicial2026", "debil"))
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void crearEstudianteSinCarreraDevuelve400() throws Exception {
        when(usuarioService.crear(any())).thenThrow(new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos",
                "Para el rol ESTUDIANTE son obligatorios registro e idCarrera."));
        llamar(post("/api/v1/usuarios"), admin(), CREAR_ESTUDIANTE)
                .andExpect(status().isBadRequest()).andExpect(cumpleContrato());
    }

    @Test
    void crearDuplicadoDevuelve409YCarreraInexistente404() throws Exception {
        when(usuarioService.crear(any())).thenThrow(NegocioException.conflicto("Ya existe una persona con el CI 5554443."));
        llamar(post("/api/v1/usuarios"), admin(), CREAR_DOCENTE)
                .andExpect(status().isConflict()).andExpect(cumpleContrato());

        doThrow(NegocioException.noEncontrado("No existe la carrera 10.")).when(usuarioService).crear(any());
        llamar(post("/api/v1/usuarios"), admin(), CREAR_ESTUDIANTE)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void obtenerActualizarYDesactivar() throws Exception {
        UsuarioResponse docente = PadronMock.docenteResponse();
        when(usuarioService.obtener(5)).thenReturn(docente);
        when(usuarioService.actualizar(any(), eq(5), any())).thenReturn(docente);
        when(usuarioService.desactivar(any(), eq(5))).thenReturn(new UsuarioResponse(5, "doc.maria", "DOCENTE",
                Estado.INACTIVO, true, false, 5, "Maria", "Lopez Suarez", "5554443", "maria.lopez@uagrm.edu.bo",
                null, null, null, null, null));

        llamar(get("/api/v1/usuarios/5"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/usuarios/5"), admin(), ACTUALIZAR).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/usuarios/5"), admin(), ACTUALIZAR.replace(",\n \"estado\":1", ""))
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/usuarios/5"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void usuarioInexistenteDevuelve404() throws Exception {
        when(usuarioService.obtener(99)).thenThrow(NegocioException.noEncontrado("No existe el usuario 99."));
        llamar(get("/api/v1/usuarios/99"), admin(), null).andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void adminNoPuedeDesactivarseASiMismo() throws Exception {
        when(usuarioService.desactivar(any(), eq(1))).thenThrow(new NegocioException(HttpStatus.BAD_REQUEST,
                "Datos invalidos", "No puede desactivar su propia cuenta."));
        llamar(delete("/api/v1/usuarios/1"), admin(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContrato());
    }

    @Test
    void actualizarConCorreoDeOtraPersonaDevuelve409() throws Exception {
        when(usuarioService.actualizar(any(), eq(5), any())).thenThrow(
                NegocioException.conflicto("Ya existe una persona con el correo maria.lopez@uagrm.edu.bo."));
        llamar(put("/api/v1/usuarios/5"), admin(), ACTUALIZAR)
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void ciOCelularConFormatoInvalidoDevuelven400() throws Exception {
        llamar(post("/api/v1/usuarios"), admin(), CREAR_DOCENTE.replace("\"5554443\"", "\"12-ABCD\""))
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
        llamar(post("/api/v1/usuarios"), admin(), CREAR_DOCENTE.replace("\"+59170012345\"", "\"70-012\""))
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void restablecerPassword() throws Exception {
        llamar(post("/api/v1/usuarios/5/password/reset"), admin(), "{\"passwordNueva\":\"Temporal2026\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(usuarioService).restablecerPassword(eq(5), any());

        llamar(post("/api/v1/usuarios/5/password/reset"), admin(), "{\"passwordNueva\":\"corta\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Tipos de usuario ----------

    @Test
    void listarTiposUsuario() throws Exception {
        when(usuarioService.listarTipos()).thenReturn(PadronMock.tiposUsuario());
        llamar(get("/api/v1/tipos-usuario"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    // ---------- Padron ----------

    @Test
    void listarPadronConFiltros() throws Exception {
        when(padronService.listar(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PaginaResponse<>(List.of(PadronMock.estudianteHabilitadoResponse()), 0, 20, 1, 1));

        llamar(get("/api/v1/padron"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/padron").param("q", "7654321").param("idCarrera", "10").param("registrado", "false")
                .param("habilitado", "true").param("tamano", "100"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(padronService).listar("7654321", 10, false, true, 0, 100);
        verify(padronService).listar(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void actualizarHabilitado() throws Exception {
        EstudianteHabilitadoResponse inhabilitado = new EstudianteHabilitadoResponse(70, PadronMock.REGISTRO,
                PadronMock.CI, "juan.perez@uagrm.edu.bo", "Juan Carlos", "Perez Lopez", 10, "INF", false, true);
        when(padronService.actualizarHabilitado(eq(70), any())).thenReturn(inhabilitado);
        when(padronService.actualizarHabilitado(eq(99), any()))
                .thenThrow(NegocioException.noEncontrado("No existe el registro 99 del padron."));

        llamar(patch("/api/v1/padron/70"), admin(), "{\"habilitado\":false}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/padron/99"), admin(), "{\"habilitado\":true}")
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/padron/70"), admin(), "{}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    /** Ver MULTIPART: el cuerpo multipart no se valida contra el contrato; todo lo demas si. */
    @Test
    void importarPadron() throws Exception {
        when(padronService.importar(any())).thenReturn(PadronMock.resultadoImportacion());

        ResultActions resultado = mvc.perform(multipart("/api/v1/padron/import").file(archivoCsv())
                        .header("Authorization", admin())
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isOk()).andExpect(cumpleContratoMultipart());
        assertThat(resultado.andReturn().getResponse().getContentAsString()).contains("\"columna\":null");
    }

    @Test
    void importarArchivoInvalidoDevuelve400() throws Exception {
        when(padronService.importar(any())).thenThrow(new NegocioException(HttpStatus.BAD_REQUEST, "Archivo invalido",
                "Formato no soportado: el archivo debe ser .csv, .xlsx o .xls."));

        mvc.perform(multipart("/api/v1/padron/import").file(archivoCsv()).header("Authorization", admin())
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoMultipart());
    }

    @Test
    void importarSinArchivoDevuelve400() throws Exception {
        mvc.perform(multipart("/api/v1/padron/import").header("Authorization", admin())
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void docenteNoPuedeImportar() throws Exception {
        mvc.perform(multipart("/api/v1/padron/import").file(archivoCsv()).header("Authorization", docente())
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isForbidden()).andExpect(cumpleContratoMultipart());
    }

    // ---------- Dominios ----------

    @Test
    void listarDominiosConYSinFiltro() throws Exception {
        when(dominioService.listar(any())).thenReturn(List.of(loginIntentos(), DominioMock.response(3, "UI_GROUP",
                "TEMA", "claro", false)));

        llamar(get("/api/v1/dominios"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/dominios").param("domainGroup", ConstantesDominio.GRUPO_AUTH), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(dominioService).listar(ConstantesDominio.GRUPO_AUTH);
        llamar(get("/api/v1/dominios").param("domainGroup", "1-malo"), admin(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void crearObtenerActualizarYEliminarDominio() throws Exception {
        DominioResponse tema = DominioMock.response(3, "UI_GROUP", "TEMA", "claro", false);
        when(dominioService.crear(any(), any())).thenReturn(tema);
        when(dominioService.obtener(1)).thenReturn(loginIntentos());
        when(dominioService.actualizar(any(), eq(1), any())).thenReturn(loginIntentos());

        llamar(post("/api/v1/dominios"), admin(),
                "{\"domainGroup\":\"ui_group\",\"domainName\":\"TEMA\",\"domainValue\":\"claro\",\"description\":\"Tema\"}")
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
        llamar(get("/api/v1/dominios/1"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/dominios/1"), admin(), "{\"domainValue\":\"5\",\"description\":\"Cinco intentos\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/dominios/3"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(dominioService).eliminar(3);
    }

    @Test
    void dominioDuplicadoOInexistenteODelSistema() throws Exception {
        when(dominioService.crear(any(), any())).thenThrow(NegocioException.conflicto("Ya existe el parametro."));
        when(dominioService.obtener(99)).thenThrow(NegocioException.noEncontrado("No existe el parametro 99."));
        doThrow(NegocioException.conflicto("El parametro lo usa el sistema y no se puede eliminar."))
                .when(dominioService).eliminar(1);

        llamar(post("/api/v1/dominios"), admin(),
                "{\"domainGroup\":\"UI_GROUP\",\"domainName\":\"TEMA\",\"domainValue\":\"x\",\"description\":\"y\"}")
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
        llamar(get("/api/v1/dominios/99"), admin(), null).andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/dominios/1"), admin(), null).andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void valorInvalidoDeUnParametroDelSistemaDevuelve400ConErrorEnDomainValue() throws Exception {
        when(dominioService.actualizar(any(), eq(1), any())).thenThrow(
                NegocioException.campoInvalido("domainValue", "debe ser un numero entero entre 1 y 1000"));

        llamar(put("/api/v1/dominios/1"), admin(), "{\"domainValue\":\"0\",\"description\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.domainValue").value("debe ser un numero entero entre 1 y 1000"))
                .andExpect(cumpleContrato());
        llamar(put("/api/v1/dominios/1"), admin(), "{\"domainValue\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void unSecretoSeDevuelveSinValorYSePuedeCambiarSoloLaDescripcion() throws Exception {
        DominioResponse password = DominioMock.secreto(12, ConstantesDominio.GRUPO_SMTP, ConstantesDominio.PASSWORD, true);
        when(dominioService.actualizar(any(), eq(12), any())).thenReturn(password);
        when(dominioService.listar(any())).thenReturn(List.of(password,
                DominioMock.secreto(13, ConstantesDominio.GRUPO_SMTP, "OTRO_SECRETO", false)));

        llamar(get("/api/v1/dominios").param("domainGroup", ConstantesDominio.GRUPO_SMTP), admin(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].domainValue").value(""))
                .andExpect(jsonPath("$.data[0].secreto").value(true))
                .andExpect(jsonPath("$.data[0].configurado").value(true))
                .andExpect(jsonPath("$.data[1].configurado").value(false))
                .andExpect(cumpleContrato());
        // Valor nuevo, valor vacio (borrar el secreto) y solo descripcion (domainValue omitido o null: conserva)
        llamar(put("/api/v1/dominios/12"), admin(), "{\"domainValue\":\"nueva-clave\",\"description\":\"Clave SMTP\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.domainValue").value(""))
                .andExpect(cumpleContrato());
        llamar(put("/api/v1/dominios/12"), admin(), "{\"domainValue\":\"\",\"description\":\"Clave SMTP\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/dominios/12"), admin(), "{\"description\":\"Solo descripcion\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/dominios/12"), admin(), "{\"domainValue\":null,\"description\":\"Solo descripcion\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        // Omitido y null llegan igual al service: null = conservar
        verify(dominioService, times(2)).actualizar(any(), eq(12), eq(
                new com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest(null, "Solo descripcion")));
    }

    // ---------- Prueba SMTP ----------

    @Test
    void pruebaSmtpEnviada() throws Exception {
        llamar(post("/api/v1/config/smtp/prueba"), admin(), "{\"destinatario\":\"admin@uagrm.edu.bo\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(correoService).enviarPrueba("admin@uagrm.edu.bo");
    }

    @Test
    void pruebaSmtpSinConfigurarODestinatarioInvalidoDevuelve400() throws Exception {
        doThrow(new NegocioException(HttpStatus.BAD_REQUEST, "Configuracion de correo incompleta",
                "Falta configurar HOST del grupo SMTP_GROUP en /api/v1/dominios."))
                .when(correoService).enviarPrueba(any());

        llamar(post("/api/v1/config/smtp/prueba"), admin(), "{\"destinatario\":\"admin@uagrm.edu.bo\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContrato());
        llamar(post("/api/v1/config/smtp/prueba"), admin(), "{\"destinatario\":\"no-es-correo\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.destinatario").exists())
                .andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void pruebaSmtpConServidorQueFallaDevuelve502() throws Exception {
        doThrow(new NegocioException(HttpStatus.BAD_GATEWAY, "Error del servidor de correo",
                "No se pudo autenticar en smtp.gmail.com:587: credenciales rechazadas."))
                .when(correoService).enviarPrueba(any());

        llamar(post("/api/v1/config/smtp/prueba"), admin(), "{\"destinatario\":\"admin@uagrm.edu.bo\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("No se pudo autenticar en smtp.gmail.com:587: credenciales rechazadas."))
                .andExpect(cumpleContrato());
    }

    @Test
    void docenteNoPuedeProbarSmtp() throws Exception {
        llamar(post("/api/v1/config/smtp/prueba"), docente(), "{\"destinatario\":\"admin@uagrm.edu.bo\"}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void docenteNoAdministraDominios() throws Exception {
        llamar(get("/api/v1/dominios"), docente(), null).andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    // ---------- Notificaciones (cualquier rol, siempre las del token) ----------

    @Test
    void estudianteListaSusNotificacionesPeroNoLosUsuarios() throws Exception {
        when(notificacionService.listar(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(NotificacionMock.response(2, false), NotificacionMock.response(1, true)), 0, 20, 2, 1));

        llamar(get("/api/v1/notificaciones"), estudiante(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contenido[0].fechaLectura").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.contenido[1].enlace").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(cumpleContrato());
        llamar(get("/api/v1/notificaciones").param("soloNoLeidas", "true").param("pagina", "1").param("tamano", "5"),
                estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/usuarios"), estudiante(), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());

        // El usuario lo pone el token (sub), nunca un parametro
        ArgumentCaptor<Jwt> jwt = ArgumentCaptor.forClass(Jwt.class);
        verify(notificacionService).listar(jwt.capture(), eq(true), eq(1), eq(5));
        assertThat(jwt.getValue().getSubject()).isEqualTo(String.valueOf(ID_ESTUDIANTE));
        verify(notificacionService).listar(any(), eq(false), eq(0), eq(20));
    }

    @Test
    void paginacionDeNotificacionesFueraDeRangoDevuelve400() throws Exception {
        llamar(get("/api/v1/notificaciones").param("tamano", "101"), estudiante(), null)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void docenteYAdminConsultanSusPendientes() throws Exception {
        when(notificacionService.pendientes(any())).thenReturn(new NotificacionesPendientesResponse(3, 30));

        llamar(get("/api/v1/notificaciones/pendientes"), docente(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cantidad").value(3))
                .andExpect(jsonPath("$.data.pollingSegundos").value(30))
                .andExpect(cumpleContrato());
        llamar(get("/api/v1/notificaciones/pendientes"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void marcarUnaLeidaYUnaAjenaDevuelve404() throws Exception {
        when(notificacionService.marcarLeida(any(), eq(2))).thenReturn(NotificacionMock.response(2, true));
        when(notificacionService.marcarLeida(any(), eq(9)))
                .thenThrow(NegocioException.noEncontrado("No existe la notificacion 9."));

        llamar(patch("/api/v1/notificaciones/2/leida"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.leida").value(true))
                .andExpect(cumpleContrato());
        llamar(patch("/api/v1/notificaciones/9/leida"), estudiante(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void marcarTodasLeidas() throws Exception {
        llamar(post("/api/v1/notificaciones/leidas"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        verify(notificacionService).marcarTodasLeidas(any());
    }

    @Test
    void conPasswordPendienteTampocoVeSusNotificaciones() throws Exception {
        Usuario pendiente = UsuarioMock.usuario(ID_ESTUDIANTE, "219012345", TipoUsuario.ESTUDIANTE);
        pendiente.setRequiereCambioPassword(true);
        llamar(get("/api/v1/notificaciones/pendientes"), token(pendiente), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/perfil/preferencias"), token(pendiente), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void notificacionesSinTokenDevuelve401() throws Exception {
        llamar(get("/api/v1/notificaciones"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Perfil ----------

    @Test
    void preferenciasDelUsuarioAutenticado() throws Exception {
        when(perfilService.obtenerPreferencias(any())).thenReturn(new Preferencias(false));
        when(perfilService.actualizarPreferencias(any(), any())).thenReturn(new Preferencias(true));

        llamar(get("/api/v1/perfil/preferencias"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notificarCorreo").value(false))
                .andExpect(cumpleContrato());
        llamar(put("/api/v1/perfil/preferencias"), docente(), "{\"notificarCorreo\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notificarCorreo").value(true))
                .andExpect(cumpleContrato());
        verify(perfilService).actualizarPreferencias(any(), eq(new Preferencias(true)));
    }

    @Test
    void preferenciasSinValorDevuelve400() throws Exception {
        llamar(put("/api/v1/perfil/preferencias"), estudiante(), "{}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenDevuelve401() throws Exception {
        llamar(get("/api/v1/usuarios"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void docenteYEstudianteNoTienenAcceso() throws Exception {
        llamar(get("/api/v1/usuarios"), docente(), null).andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/tipos-usuario"), estudiante(), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/padron"), docente(), null).andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void adminConPasswordPendienteDevuelve403() throws Exception {
        Usuario admin = UsuarioMock.usuario(1, "admin", TipoUsuario.ADMIN);
        admin.setRequiereCambioPassword(true);
        llamar(get("/api/v1/usuarios"), token(admin), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void tokenRevocadoDevuelve401() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenReturn(true);
        llamar(get("/api/v1/usuarios/5"), admin(), null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
    }

    @Test
    void redisCaidoDevuelve503() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenThrow(new NegocioException(
                HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "Intente nuevamente en unos minutos."));
        llamar(get("/api/v1/tipos-usuario"), admin(), null)
                .andExpect(status().isServiceUnavailable()).andExpect(cumpleContrato());
    }

    // ---------- helpers ----------

    private static DominioResponse loginIntentos() {
        return DominioMock.response(1, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_INTENTOS, "10", true);
    }

    private static MockMultipartFile archivoCsv() {
        return new MockMultipartFile("archivo", "padron.csv", "text/csv",
                (PadronMock.CABECERA + "\n219012345,7654321,juan@uagrm.edu.bo,Uagrm2026,Juan,Perez,INF\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions llamar(MockHttpServletRequestBuilder request, String bearer, String cuerpo) throws Exception {
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        if (cuerpo != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
    }

    private String admin() {
        return token(UsuarioMock.usuario(1, "admin", TipoUsuario.ADMIN));
    }

    private String docente() {
        return token(CatalogoMock.docente());
    }

    private String estudiante() {
        return token(UsuarioMock.usuario(ID_ESTUDIANTE, "219012345", TipoUsuario.ESTUDIANTE));
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
        when(usuarioService.listar(any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(PadronMock.docenteResponse()), 0, 20, 1, 1));
        llamar(get("/api/v1/usuarios"), admin(), null)
                .andExpect(status().isOk())
                .andExpect(SeguridadHttpMock.cabecerasApi());
        llamar(get("/api/v1/usuarios"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(SeguridadHttpMock.cabecerasApi());
    }

    @Test
    void preflightCorsSoloDesdeElOrigenDelFrontend() throws Exception {
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/usuarios", SeguridadHttpMock.ORIGEN_PERMITIDO, "POST"))
                .andExpect(SeguridadHttpMock.preflightPermitido());
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/usuarios", SeguridadHttpMock.ORIGEN_AJENO, "POST"))
                .andExpect(SeguridadHttpMock.preflightRechazado());
    }

    @Test
    void errorInesperadoNoExponeMensajesNiTrazas() throws Exception {
        when(usuarioService.listar(any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException(SeguridadHttpMock.MENSAJE_INTERNO));
        llamar(get("/api/v1/usuarios"), admin(), null)
                .andExpect(SeguridadHttpMock.error500SinDetallesInternos());
    }
}
