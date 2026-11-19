package com.uagrm.repositorio.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.uagrm.repositorio.auth.config.SecurityConfig;
import com.uagrm.repositorio.auth.controller.AuthController;
import com.uagrm.repositorio.auth.controller.DosFactoresController;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.auth.ActivarDosFactoresResponse;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.RegistroResponse;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.auth.UsuarioSesionDto;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.AuthService;
import com.uagrm.repositorio.imp.service.DosFactoresService;
import com.uagrm.repositorio.imp.service.RegistroService;
import com.uagrm.repositorio.mockup.UsuarioMock;
import com.uagrm.repositorio.mockup.SeguridadHttpMock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test de contrato: verifica que los requests y responses reales del auth-service (serializacion Jackson,
 * seguridad, ProblemDetail) cumplen spec/openapi/auth-api.yaml. Si alguien cambia un DTO, un status o un
 * endpoint sin actualizar el contrato (o al reves), este test falla.
 * Los servicios estan mockeados: aqui se prueba la forma de la API, no la logica de negocio.
 */
@WebMvcTest(controllers = {AuthController.class, DosFactoresController.class})
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
class AuthContratoTest {

    private static final String CONTRATO = "openapi/auth-api.yaml";

    /** Valida request y response; rechaza propiedades que el contrato no declara. */
    private static final OpenApiInteractionValidator ESTRICTO =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRATO)
                    .build();

    /** Para los casos que envian a proposito un request invalido: solo se valida la respuesta de error. */
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
    private AuthService authService;

    @MockitoBean
    private DosFactoresService dosFactoresService;

    @MockitoBean
    private RegistroService registroService;

    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    private Usuario usuario;
    private TokenResponse tokens;

    @BeforeEach
    void setUp() {
        usuario = UsuarioMock.estudiante();
        tokens = new TokenResponse("access", "refresh", "Bearer", 3600,
                new UsuarioSesionDto(1, "est.juan", "Juan", "Perez", "est.juan@uagrm.edu.bo",
                        "ESTUDIANTE", false, false));
    }

    // ---------- login ----------

    @Test
    void loginSinDosFactores() throws Exception {
        when(authService.login(any(), any())).thenReturn(LoginResponse.completo(tokens));
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"est.juan\",\"password\":\"Secreta2026\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void loginConDosFactores() throws Exception {
        when(authService.login(any(), any())).thenReturn(LoginResponse.pendienteDosFactores("token-temporal"));
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"est.juan\",\"password\":\"Secreta2026\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void loginCredencialesIncorrectas() throws Exception {
        when(authService.login(any(), any())).thenThrow(NegocioException.noAutorizado("Usuario o contrasena incorrectos."));
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"est.juan\",\"password\":\"mala\"}")
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
    }

    @Test
    void loginDesdeUnaIpBloqueadaDevuelve429ConRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(new LimiteIntentosException(900));
        llamar(post("/api/v1/auth/login").with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }), "{\"usuario\":\"est.juan\",\"password\":\"mala\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos fallidos. Intente de nuevo en 15 minutos."))
                .andExpect(cumpleContrato());
        // El service recibe la IP del cliente
        verify(authService).login(any(), eq("203.0.113.7"));
    }

    @Test
    void loginConCamposVaciosDevuelveErroresPorCampo() throws Exception {
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- register (publico) ----------

    private static final String REGISTRO = "{\"ci\":\"7654321-1A\",\"registro\":219012345}";

    @Test
    void registrarEstudianteSinToken() throws Exception {
        when(registroService.registrar(any(), any())).thenReturn(new RegistroResponse("219012345"));
        llamar(post("/api/v1/auth/register"), REGISTRO)
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
    }

    @Test
    void registrarNoEncontradoEnElPadronDevuelve404() throws Exception {
        when(registroService.registrar(any(), any())).thenThrow(new NegocioException(HttpStatus.NOT_FOUND,
                "No encontrado en el padron", "No encontrado en el padron institucional. Contacte a su facultad."));
        llamar(post("/api/v1/auth/register"), REGISTRO)
                .andExpect(status().isNotFound()).andExpect(cumpleContrato());
    }

    @Test
    void registrarConCuentaExistenteDevuelve409() throws Exception {
        when(registroService.registrar(any(), any())).thenThrow(new NegocioException(HttpStatus.CONFLICT, "Cuenta existente",
                "Ya tiene una cuenta registrada. Si olvido su contrasena, contacte a su facultad."));
        llamar(post("/api/v1/auth/register"), REGISTRO)
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void registrarDesdeUnaIpBloqueadaDevuelve429ConRetryAfter() throws Exception {
        when(registroService.registrar(any(), any())).thenThrow(new LimiteIntentosException(840));
        llamar(post("/api/v1/auth/register"), REGISTRO)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "840"))
                .andExpect(cumpleContrato());
    }

    @Test
    void registrarConCiConComplementoEnMinusculasEsValido() throws Exception {
        when(registroService.registrar(any(), any())).thenReturn(new RegistroResponse("219012345"));
        llamar(post("/api/v1/auth/register"), "{\"ci\":\"1234567-1a\",\"registro\":219012345}")
                .andExpect(status().isCreated()).andExpect(cumpleContrato());
    }

    @Test
    void registrarConDatosInvalidosDevuelve400() throws Exception {
        llamar(post("/api/v1/auth/register"), "{\"ci\":-1}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    // ---------- 2fa/verify y refresh ----------

    @Test
    void verificarDosFactores() throws Exception {
        when(authService.verificarDosFactores(any())).thenReturn(tokens);
        llamar(post("/api/v1/auth/2fa/verify"), "{\"tokenTemporal\":\"t\",\"codigo\":\"123456\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void verificarDosFactoresDemasiadosIntentos() throws Exception {
        when(authService.verificarDosFactores(any())).thenThrow(new NegocioException(
                HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos", "Inicie sesion nuevamente."));
        llamar(post("/api/v1/auth/2fa/verify"), "{\"tokenTemporal\":\"t\",\"codigo\":\"000000\"}")
                .andExpect(status().isTooManyRequests()).andExpect(cumpleContrato());
    }

    @Test
    void refrescar() throws Exception {
        when(authService.refrescar(any())).thenReturn(tokens);
        llamar(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"r\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    // ---------- endpoints autenticados ----------

    @Test
    void logout() throws Exception {
        llamar(post("/api/v1/auth/logout").header("Authorization", bearer(false)), "{\"refreshToken\":\"r\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void logoutSinTokenEsRechazado() throws Exception {
        llamar(post("/api/v1/auth/logout"), null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void cambiarPassword() throws Exception {
        when(authService.cambiarPassword(any(), any())).thenReturn(tokens);
        llamar(post("/api/v1/auth/password/change").header("Authorization", bearer(true)),
                "{\"passwordActual\":\"Admin2026.\",\"passwordNueva\":\"NuevaClave2026\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void cambiarPasswordDebil() throws Exception {
        llamar(post("/api/v1/auth/password/change").header("Authorization", bearer(true)),
                "{\"passwordActual\":\"Admin2026.\",\"passwordNueva\":\"abc\"}")
                .andExpect(status().isBadRequest()).andExpect(cumpleContratoSoloRespuesta());
    }

    @Test
    void conPasswordPendienteNoPuedeActivarDosFactores() throws Exception {
        llamar(post("/api/v1/auth/2fa/enable").header("Authorization", bearer(true)), null)
                .andExpect(status().isForbidden()).andExpect(cumpleContrato());
    }

    @Test
    void activarDosFactores() throws Exception {
        when(dosFactoresService.iniciarActivacion(any())).thenReturn(new ActivarDosFactoresResponse(
                "JBSWY3DPEHPK3PXP", "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", "data:image/png;base64,iVBORw0KGgo="));
        llamar(post("/api/v1/auth/2fa/enable").header("Authorization", bearer(false)), null)
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void confirmarDosFactores() throws Exception {
        llamar(post("/api/v1/auth/2fa/confirm").header("Authorization", bearer(false)), "{\"codigo\":\"123456\"}")
                .andExpect(status().isOk()).andExpect(cumpleContrato());
    }

    @Test
    void desactivarDosFactoresSinTenerloActivo() throws Exception {
        doThrow(NegocioException.conflicto("La verificacion en dos pasos no esta activada."))
                .when(dosFactoresService).desactivar(any(), any());
        llamar(post("/api/v1/auth/2fa/disable").header("Authorization", bearer(false)), "{\"codigo\":\"123456\"}")
                .andExpect(status().isConflict()).andExpect(cumpleContrato());
    }

    @Test
    void tokenRevocadoEsRechazado() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenReturn(true);
        llamar(post("/api/v1/auth/2fa/enable").header("Authorization", bearer(false)), null)
                .andExpect(status().isUnauthorized()).andExpect(cumpleContrato());
    }

    @Test
    void redisCaidoDevuelve503() throws Exception {
        when(revocacionTokenService.estaRevocado(any())).thenThrow(new NegocioException(
                HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "Intente nuevamente en unos minutos."));
        llamar(post("/api/v1/auth/2fa/enable").header("Authorization", bearer(false)), null)
                .andExpect(status().isServiceUnavailable()).andExpect(cumpleContrato());
    }

    // ---------- helpers ----------

    private ResultActions llamar(MockHttpServletRequestBuilder request, String cuerpo) throws Exception {
        if (cuerpo != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Access token real (firmado con el JwtService real) para pasar por el resource server real. */
    private String bearer(boolean requiereCambioPassword) {
        usuario.setRequiereCambioPassword(requiereCambioPassword);
        return "Bearer " + jwtService.generar(usuario, Duration.ofMinutes(5)).accessToken();
    }

    private static ResultMatcher cumpleContrato() {
        return OpenApiValidationMatchers.openApi().isValid(ESTRICTO);
    }

    private static ResultMatcher cumpleContratoSoloRespuesta() {
        return OpenApiValidationMatchers.openApi().isValid(SOLO_RESPUESTA);
    }

    // ---------- hardening HTTP (S5): cabeceras de seguridad, CORS y 500 sin detalles ----------

    @Test
    void cabecerasDeSeguridadEnRespuestas200y401() throws Exception {
        when(authService.login(any(), any())).thenReturn(LoginResponse.completo(tokens));
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"est.juan\",\"password\":\"Secreta2026\"}")
                .andExpect(status().isOk())
                .andExpect(SeguridadHttpMock.cabecerasApi());
        llamar(post("/api/v1/auth/2fa/enable"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(SeguridadHttpMock.cabecerasApi());
    }

    @Test
    void preflightCorsSoloDesdeElOrigenDelFrontend() throws Exception {
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/auth/login", SeguridadHttpMock.ORIGEN_PERMITIDO, "POST"))
                .andExpect(SeguridadHttpMock.preflightPermitido());
        mvc.perform(SeguridadHttpMock.preflight("/api/v1/auth/login", SeguridadHttpMock.ORIGEN_AJENO, "POST"))
                .andExpect(SeguridadHttpMock.preflightRechazado());
    }

    @Test
    void errorInesperadoNoExponeMensajesNiTrazas() throws Exception {
        when(authService.login(any(), any())).thenThrow(new IllegalStateException(SeguridadHttpMock.MENSAJE_INTERNO));
        llamar(post("/api/v1/auth/login"), "{\"usuario\":\"est.juan\",\"password\":\"Secreta2026\"}")
                .andExpect(SeguridadHttpMock.error500SinDetallesInternos());
    }
}
