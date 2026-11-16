package com.uagrm.repositorio.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.uagrm.repositorio.catalog.config.SecurityConfig;
import com.uagrm.repositorio.catalog.controller.CarreraController;
import com.uagrm.repositorio.catalog.controller.ConfigFacultadController;
import com.uagrm.repositorio.catalog.controller.FacultadController;
import com.uagrm.repositorio.catalog.controller.FaseController;
import com.uagrm.repositorio.catalog.controller.GrupoController;
import com.uagrm.repositorio.catalog.controller.MateriaController;
import com.uagrm.repositorio.catalog.controller.SemestreController;
import com.uagrm.repositorio.catalog.controller.TipoTrabajoController;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.catalogo.CarreraService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.catalogo.FacultadService;
import com.uagrm.repositorio.imp.service.catalogo.FaseService;
import com.uagrm.repositorio.imp.service.catalogo.GrupoService;
import com.uagrm.repositorio.imp.service.catalogo.MateriaService;
import com.uagrm.repositorio.imp.service.catalogo.SemestreService;
import com.uagrm.repositorio.imp.service.catalogo.TipoTrabajoService;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.ConfigFacultadMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test de contrato: verifica que los requests y responses reales del catalog-service (serializacion Jackson,
 * seguridad por rol, ProblemDetail) cumplen spec/openapi/catalog-api.yaml. Cubre todos los endpoints.
 * Los servicios estan mockeados: aqui se prueba la forma de la API, no la logica de negocio.
 */
@WebMvcTest(controllers = {FacultadController.class, ConfigFacultadController.class, CarreraController.class, MateriaController.class,
        SemestreController.class, GrupoController.class, FaseController.class, TipoTrabajoController.class})
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
class CatalogContratoTest {

    private static final String CONTRATO = "openapi/catalog-api.yaml";

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

    private static final String FACULTAD = "{\"nombre\":\"Facultad de Tecnologia\",\"sigla\":\"FCT\"}";
    private static final String CARRERA = "{\"nombre\":\"Ingenieria Informatica\",\"sigla\":\"INF\",\"idFacultad\":1}";
    private static final String MATERIA = "{\"nombre\":\"Ingenieria de Software I\",\"sigla\":\"INF412\",\"idFacultad\":1}";
    private static final String SEMESTRE = "{\"semestre\":2,\"anio\":2026,\"idFacultad\":1,\"estado\":1}";
    private static final String GRUPO = "{\"nombre\":\"SA\",\"idMateria\":20,\"idSemestre\":30}";
    private static final String FASE = "{\"nombre\":\"Perfil\",\"descripcion\":\"Propuesta inicial\",\"idFacultad\":1}";
    private static final String CONFIG = """
            {"tamanoMaxArchivoMB":20,"umbralPlagioMedio":25,"umbralPlagioAlto":60,"comparacionInternet":true,
             "accesoExterno":false,"escalaCalifMax":70,"jwtExpiracionHoras":8}""";
    private static final String TIPO = "{\"nombre\":\"Proyecto de grado\",\"idFacultad\":1}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private FacultadService facultadService;
    @MockitoBean
    private CarreraService carreraService;
    @MockitoBean
    private ConfigFacultadService configFacultadService;
    @MockitoBean
    private MateriaService materiaService;
    @MockitoBean
    private SemestreService semestreService;
    @MockitoBean
    private GrupoService grupoService;
    @MockitoBean
    private FaseService faseService;
    @MockitoBean
    private TipoTrabajoService tipoTrabajoService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    // ---------- Configuracion por facultad ----------

    @Test
    void configFacultadSinPersonalizarYGuardada() throws Exception {
        when(configFacultadService.obtener(1)).thenReturn(ConfigFacultadMock.porDefecto(1));
        when(configFacultadService.actualizar(eq(1), any())).thenReturn(ConfigFacultadMock.personalizada(1, 8));

        llamar(get("/api/v1/facultades/1/config"), estudiante(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalizada").value(false))
                .andExpect(cumpleContrato());
        llamar(get("/api/v1/facultades/1/config"), docente(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(put("/api/v1/facultades/1/config"), admin(), CONFIG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalizada").value(true))
                .andExpect(cumpleContrato());
    }

    @Test
    void soloAdminGuardaLaConfigFacultad() throws Exception {
        llamar(put("/api/v1/facultades/1/config"), estudiante(), CONFIG)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(put("/api/v1/facultades/1/config"), docente(), CONFIG)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
        llamar(get("/api/v1/facultades/1/config"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void configFacultadInvalidaOInexistente() throws Exception {
        when(configFacultadService.obtener(99)).thenThrow(NegocioException.noEncontrado("No existe la facultad 99."));
        when(configFacultadService.actualizar(eq(1), any())).thenThrow(
                NegocioException.campoInvalido("umbralPlagioMedio", "debe ser menor que umbralPlagioAlto"));

        llamar(get("/api/v1/facultades/99/config"), estudiante(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
        llamar(put("/api/v1/facultades/1/config"), admin(), CONFIG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.umbralPlagioMedio").value("debe ser menor que umbralPlagioAlto"))
                .andExpect(cumpleContrato());
        llamar(put("/api/v1/facultades/1/config"), admin(), CONFIG.replace("\"jwtExpiracionHoras\":8", "\"jwtExpiracionHoras\":25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.jwtExpiracionHoras").exists())
                .andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- Facultades ----------

    @Test
    void facultadesCrud() throws Exception {
        FacultadResponse ficct = CatalogoMock.facultadResponse();
        when(facultadService.listar(anyBoolean())).thenReturn(List.of(ficct));
        when(facultadService.crear(any())).thenReturn(ficct);
        when(facultadService.actualizar(eq(1), any())).thenReturn(ficct);
        when(facultadService.desactivar(1)).thenReturn(
                new FacultadResponse(1, ficct.nombre(), ficct.sigla(), Estado.INACTIVO));

        llamar(get("/api/v1/facultades"), estudiante(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/facultades").param("incluirInactivos", "true"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(post("/api/v1/facultades"), admin(), FACULTAD)
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
        llamar(put("/api/v1/facultades/1"), admin(), "{\"nombre\":\"FICCT\",\"sigla\":\"FICCT\",\"estado\":1}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/facultades/1"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    // ---------- Carreras, materias, semestres, fases, tipos de trabajo ----------

    @Test
    void carrerasCrud() throws Exception {
        when(carreraService.listar(any(), anyBoolean())).thenReturn(List.of(CatalogoMock.carreraResponse()));
        when(carreraService.crear(any())).thenReturn(CatalogoMock.carreraResponse());
        when(carreraService.actualizar(eq(10), any())).thenReturn(CatalogoMock.carreraResponse());
        when(carreraService.desactivar(10)).thenReturn(CatalogoMock.carreraResponse());

        crud("/api/v1/carreras", "/api/v1/carreras/10", CARRERA);
    }

    @Test
    void materiasCrud() throws Exception {
        when(materiaService.listar(any(), anyBoolean())).thenReturn(List.of(CatalogoMock.materiaResponse()));
        when(materiaService.crear(any())).thenReturn(CatalogoMock.materiaResponse());
        when(materiaService.actualizar(eq(20), any())).thenReturn(CatalogoMock.materiaResponse());
        when(materiaService.desactivar(20)).thenReturn(CatalogoMock.materiaResponse());

        crud("/api/v1/materias", "/api/v1/materias/20", MATERIA);
    }

    @Test
    void semestresCrud() throws Exception {
        when(semestreService.listar(any(), anyBoolean())).thenReturn(List.of(CatalogoMock.semestreResponse()));
        when(semestreService.crear(any())).thenReturn(CatalogoMock.semestreResponse());
        when(semestreService.actualizar(eq(30), any())).thenReturn(CatalogoMock.semestreResponse());
        when(semestreService.desactivar(30)).thenReturn(CatalogoMock.semestreResponse());

        crud("/api/v1/semestres", "/api/v1/semestres/30", SEMESTRE);
    }

    @Test
    void fasesCrud() throws Exception {
        when(faseService.listar(any(), anyBoolean())).thenReturn(List.of(CatalogoMock.faseResponse()));
        when(faseService.crear(any())).thenReturn(CatalogoMock.faseResponse());
        when(faseService.actualizar(eq(50), any())).thenReturn(CatalogoMock.faseResponse());
        when(faseService.desactivar(50)).thenReturn(CatalogoMock.faseResponse());

        crud("/api/v1/fases", "/api/v1/fases/50", FASE);
    }

    @Test
    void tiposTrabajoCrud() throws Exception {
        when(tipoTrabajoService.listar(any(), anyBoolean())).thenReturn(List.of(CatalogoMock.tipoTrabajoResponse()));
        when(tipoTrabajoService.crear(any())).thenReturn(CatalogoMock.tipoTrabajoResponse());
        when(tipoTrabajoService.actualizar(eq(60), any())).thenReturn(CatalogoMock.tipoTrabajoResponse());
        when(tipoTrabajoService.desactivar(60)).thenReturn(CatalogoMock.tipoTrabajoResponse());

        crud("/api/v1/tipos-trabajo", "/api/v1/tipos-trabajo/60", TIPO);
    }

    // ---------- Grupos y docentes ----------

    @Test
    void gruposCrud() throws Exception {
        when(grupoService.listar(any(), any(), any(), anyBoolean())).thenReturn(List.of(CatalogoMock.grupoResponse()));
        when(grupoService.crear(any())).thenReturn(CatalogoMock.grupoResponse());
        when(grupoService.actualizar(eq(40), any())).thenReturn(CatalogoMock.grupoResponse());
        when(grupoService.desactivar(40)).thenReturn(CatalogoMock.grupoResponse());

        llamar(get("/api/v1/grupos").param("idMateria", "20").param("idSemestre", "30"), docente(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get("/api/v1/grupos").param("idFacultad", "1"), docente(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(post("/api/v1/grupos"), admin(), GRUPO).andExpect(status().isCreated()).andExpect(cumpleContrato());
        llamar(put("/api/v1/grupos/40"), admin(), GRUPO).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/grupos/40"), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void docentesDelGrupo() throws Exception {
        when(grupoService.listarDocentes(40)).thenReturn(List.of(CatalogoMock.docenteGrupoResponse()));
        when(grupoService.asignarDocente(eq(40), any())).thenReturn(CatalogoMock.docenteGrupoResponse());
        when(grupoService.actualizarDocente(eq(40), eq(5), any())).thenReturn(CatalogoMock.docenteGrupoResponse());

        llamar(get("/api/v1/grupos/40/docentes"), docente(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(post("/api/v1/grupos/40/docentes"), admin(), "{\"idUsuario\":5,\"esAdminCurso\":true}")
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
        llamar(patch("/api/v1/grupos/40/docentes/5"), admin(), "{\"esAdminCurso\":false}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete("/api/v1/grupos/40/docentes/5"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void docenteNoPuedeCambiarEsAdminCurso() throws Exception {
        llamar(patch("/api/v1/grupos/40/docentes/5"), docente(), "{\"esAdminCurso\":true}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void cambiarEsAdminCursoDeDocenteNoAsignadoDevuelve404() throws Exception {
        when(grupoService.actualizarDocente(eq(40), eq(5), any())).thenThrow(
                NegocioException.noEncontrado("El usuario 5 no esta asignado al grupo 40."));

        llamar(patch("/api/v1/grupos/40/docentes/5"), admin(), "{\"esAdminCurso\":true}")
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void asignarUsuarioQueNoEsDocenteDevuelve400() throws Exception {
        when(grupoService.asignarDocente(eq(40), any())).thenThrow(
                new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos", "El usuario est.juan no es docente."));

        llamar(post("/api/v1/grupos/40/docentes"), admin(), "{\"idUsuario\":1,\"esAdminCurso\":false}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContrato());
    }

    @Test
    void quitarDocenteNoAsignadoDevuelve404() throws Exception {
        doThrow(NegocioException.noEncontrado("El usuario 5 no esta asignado al grupo 40."))
                .when(grupoService).quitarDocente(40, 5);

        llamar(delete("/api/v1/grupos/40/docentes/5"), admin(), null)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    // ---------- Errores ----------

    @Test
    void sinTokenDevuelve401() throws Exception {
        llamar(get("/api/v1/carreras"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void estudianteNoPuedeEscribir() throws Exception {
        llamar(post("/api/v1/carreras"), estudiante(), CARRERA)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void docenteNoPuedeAsignarDocentes() throws Exception {
        llamar(post("/api/v1/grupos/40/docentes"), docente(), "{\"idUsuario\":5,\"esAdminCurso\":true}")
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void conPasswordPendienteNoPuedeLeer() throws Exception {
        Usuario admin = UsuarioMock.usuario(1, "admin", TipoUsuario.ADMIN);
        admin.setRequiereCambioPassword(true);
        llamar(get("/api/v1/facultades"), token(admin), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void cuerpoInvalidoDevuelveErroresPorCampo() throws Exception {
        llamar(post("/api/v1/materias"), admin(), "{\"nombre\":\"\",\"sigla\":\"INF412\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        llamar(put("/api/v1/materias/abc"), admin(), MATERIA)
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void registroInexistenteDevuelve404() throws Exception {
        when(semestreService.actualizar(eq(99), any())).thenThrow(NegocioException.noEncontrado("No existe el semestre 99."));

        llamar(put("/api/v1/semestres/99"), admin(), SEMESTRE)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void duplicadoDevuelve409() throws Exception {
        when(carreraService.crear(any())).thenThrow(NegocioException.conflicto("Ya existe una carrera con la sigla INF."));

        llamar(post("/api/v1/carreras"), admin(), CARRERA)
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void tokenRevocadoDevuelve401() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenReturn(true);

        llamar(get("/api/v1/fases"), estudiante(), null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
    }

    @Test
    void redisCaidoEnLaVerificacionDeSesionDevuelve503() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenThrow(new NegocioException(
                HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "Intente nuevamente en unos minutos."));

        llamar(get("/api/v1/tipos-trabajo"), estudiante(), null)
                .andExpect(status().isServiceUnavailable()).andExpect(cumpleContrato());
    }

    // ---------- helpers ----------

    /** GET filtrado por facultad (lectura de un estudiante), POST, PUT y DELETE (ADMIN). */
    private void crud(String coleccion, String recurso, String cuerpo) throws Exception {
        llamar(get(coleccion).param("idFacultad", "1"), estudiante(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(get(coleccion).param("idFacultad", "1").param("incluirInactivos", "true"), admin(), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(post(coleccion), admin(), cuerpo).andExpect(status().isCreated()).andExpect(cumpleContrato());
        llamar(put(recurso), admin(), cuerpo).andExpect(status().isOk()).andExpect(cumpleContrato());
        llamar(delete(recurso), admin(), null).andExpect(status().isOk()).andExpect(cumpleContrato());
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
        return token(UsuarioMock.estudiante());
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
