package com.uagrm.repositorio.work.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.EstadoAccesoPublicoResponse;
import com.uagrm.repositorio.imp.config.SeguridadConfig;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.redis.LimitePublicoService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import com.uagrm.repositorio.imp.service.documento.DocumentoService;
import com.uagrm.repositorio.imp.service.trabajo.RepositorioService;
import com.uagrm.repositorio.imp.service.trabajo.TrabajoService;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.work.controller.PublicoController;
import com.uagrm.repositorio.work.controller.RepositorioController;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Limite por IP del repositorio publico de punta a punta (filtro real + LimitePublicoService real, Redis simulado
 * en memoria): 60 consultas pasan, la 61 da 429 ProblemDetail con Retry-After, otra IP no se ve afectada, Redis
 * caido deja pasar y las rutas autenticadas (o un metodo distinto de GET) no cuentan.
 */
@WebMvcTest(controllers = {PublicoController.class, RepositorioController.class})
@Import({SecurityConfig.class, SeguridadConfig.class, SeguridadErrorHandler.class, JwtService.class,
        LimitePublicoService.class})
@TestPropertySource(properties = {
        "app.seguridad.jwt.secreto=c2VjcmV0by1zb2xvLXBhcmEtdGVzdHMtZGUtY29udHJhdG8tMzJieXRlcw==",
        "app.seguridad.jwt.emisor=repositorio-uagrm",
        "app.seguridad.jwt.refresh-ttl=7d",
        "app.seguridad.dos-factores.emisor=Repositorio UAGRM",
        "app.seguridad.dos-factores.ttl-pendiente=300s",
        "app.seguridad.dos-factores.max-intentos=5",
        "app.seguridad.cors-origenes=http://localhost:4200"
})
class LimitePublicoFilterTest {

    private static final String IP = "200.87.10.5";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private StringRedisTemplate redis;
    @MockitoBean
    private ParametrosService parametros;
    @MockitoBean
    private RepositorioService repositorioService;
    @MockitoBean
    private TrabajoService trabajoService;
    @MockitoBean
    private DocumentoService documentoService;
    @MockitoBean
    private RevocacionTokenService revocacionTokenService;

    private final Map<String, Long> contadores = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(parametros.entero(Parametro.WORK_PUBLICO_PETICIONES_POR_MINUTO)).thenReturn(60);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenAnswer(inv ->
                contadores.merge(inv.<List<String>>getArgument(1).getFirst(), 1L, Long::sum));
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(42L);
        when(repositorioService.estadoPublico()).thenReturn(new EstadoAccesoPublicoResponse(true, List.of(1)));
        when(repositorioService.buscarPublico(any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.repositorioResumen()), 0, 20, 1, 1));
        when(repositorioService.buscar(any(), anyInt(), anyInt())).thenReturn(new PaginaResponse<>(
                List.of(TrabajoMock.repositorioResumen()), 0, 20, 1, 1));
    }

    @Test
    void sesentaPasanYLaSesentaYUnoDa429ConRetryAfter() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(desde(get("/api/v1/publico/estado"), IP)).andExpect(status().isOk());
        }
        mvc.perform(desde(get("/api/v1/publico/estado"), IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("60 consultas por minuto")))
                .andExpect(jsonPath("$.timestamp").exists());
        // las tres rutas publicas comparten el contador de la IP
        mvc.perform(desde(get("/api/v1/publico/repositorio"), IP)).andExpect(status().isTooManyRequests());
        mvc.perform(desde(get("/api/v1/publico/repositorio/7"), IP)).andExpect(status().isTooManyRequests());
        // otra IP tiene su propio contador
        mvc.perform(desde(get("/api/v1/publico/estado"), "200.87.10.6")).andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisCaidoDejaPasar() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("caido"));
        for (int i = 0; i < 70; i++) {
            mvc.perform(desde(get("/api/v1/publico/estado"), IP)).andExpect(status().isOk());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rutasAutenticadasYOtrosMetodosNoCuentan() throws Exception {
        String bearer = "Bearer " + jwtService.generar(TrabajoMock.autor(), Duration.ofMinutes(5)).accessToken();
        for (int i = 0; i < 70; i++) {
            mvc.perform(desde(get("/api/v1/repositorio"), IP).header("Authorization", bearer))
                    .andExpect(status().isOk());
        }
        mvc.perform(desde(post("/api/v1/publico/repositorio"), IP)).andExpect(status().isUnauthorized());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    private static MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder request, String ip) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
    }
}
