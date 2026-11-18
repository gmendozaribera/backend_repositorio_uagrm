package com.uagrm.repositorio.plagio.copyleaks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import com.uagrm.repositorio.plagio.config.PlagioProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente y motor de Copyleaks contra un servidor HTTP falso (MockRestServiceServer): login con email + API key,
 * token cacheado en memoria, un PUT por archivo con base64, webhook con {STATUS}, sandbox e internet; renovacion
 * del token ante un 401; error del proveedor.
 */
class MotorCopyleaksTest {

    private static final String LOGIN = "https://id.copyleaks.com/v3/account/login/api";
    private static final String SUBMIT = "https://api.copyleaks.com/v3/scans/submit/file/";
    private static final String TOKEN_WEBHOOK = "w".repeat(40);
    private static final String SCAN = "uagrm-0123456789abcdef0123";

    private final AlmacenamientoArchivos almacenamiento = mock(AlmacenamientoArchivos.class);
    private MockRestServiceServer servidor;
    private MotorCopyleaks motor;

    @BeforeEach
    void setUp() {
        PlagioProperties props = props("");
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        motor = new MotorCopyleaks(new CopyleaksCliente(props, builder), almacenamiento, props);
        when(almacenamiento.descargar("trabajos/7/a.pdf"))
                .thenReturn(new ByteArrayInputStream("%PDF-1.4 hola".getBytes(StandardCharsets.US_ASCII)));
        when(almacenamiento.descargar("trabajos/7/b.docx"))
                .thenReturn(new ByteArrayInputStream("PK docx".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void loginUnaVezYUnEnvioPorArchivo() {
        servidor.expect(once(), requestTo(LOGIN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value("plagio@uagrm.edu.bo"))
                .andExpect(jsonPath("$.key").value("00000000-1111-2222-3333-444444444444"))
                .andRespond(withSuccess(login("tok-1", Instant.now().plusSeconds(48 * 3600)), MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo(SUBMIT + SCAN + "-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.base64").value(Base64.getEncoder().encodeToString(
                        "%PDF-1.4 hola".getBytes(StandardCharsets.US_ASCII))))
                .andExpect(jsonPath("$.filename").value("a.pdf"))
                .andExpect(jsonPath("$.properties.sandbox").value(true))
                .andExpect(jsonPath("$.properties.webhooks.status").value(
                        "https://api.uagrm.edu.bo/api/v1/plagio/webhook/copyleaks/" + TOKEN_WEBHOOK + "/{STATUS}/" + SCAN + "-1"))
                .andExpect(jsonPath("$.properties.scanning.internet").value(true))
                .andExpect(jsonPath("$.properties.scanning.repositories").doesNotExist())
                .andExpect(jsonPath("$.properties.indexing").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));
        servidor.expect(once(), requestTo(SUBMIT + SCAN + "-2"))
                .andExpect(header("Authorization", "Bearer tok-1"))   // token reutilizado: sin segundo login
                .andExpect(jsonPath("$.filename").value("b.docx"))
                .andRespond(withStatus(HttpStatus.CREATED));

        motor.enviar(solicitud(true));

        servidor.verify();
        assertThat(motor.tipo()).isEqualTo(TipoMotorPlagio.COPYLEAKS);
    }

    @Test
    void tokenRechazadoSeRenuevaUnaVez() {
        servidor.expect(once(), requestTo(LOGIN))
                .andRespond(withSuccess(login("viejo", Instant.now().plusSeconds(3600)), MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo(SUBMIT + SCAN + "-1")).andExpect(header("Authorization", "Bearer viejo"))
                .andRespond(withUnauthorizedRequest());
        servidor.expect(once(), requestTo(LOGIN))
                .andRespond(withSuccess(login("nuevo", Instant.now().plusSeconds(3600)), MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo(SUBMIT + SCAN + "-1")).andExpect(header("Authorization", "Bearer nuevo"))
                .andRespond(withStatus(HttpStatus.CREATED));

        motor.enviar(new SolicitudAnalisis(90, 7, 1, SCAN, false, List.of(
                new SolicitudAnalisis.DocumentoAnalisis(1, "a.pdf", "trabajos/7/a.pdf", "a".repeat(64)))));
        servidor.verify();
    }

    @Test
    void errorDelProveedorSePropagaParaPasarAError() {
        servidor.expect(once(), requestTo(LOGIN))
                .andRespond(withSuccess(login("tok", Instant.now().plusSeconds(3600)), MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo(SUBMIT + SCAN + "-1")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> motor.enviar(solicitud(false))).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void conRepositorioPrivadoIndexaYComparaContraEl() {
        MotorCopyleaks conRepo = new MotorCopyleaks(new CopyleaksCliente(props("repo-uagrm"), RestClient.builder()),
                almacenamiento, props("repo-uagrm"));
        var propiedades = conRepo.propiedades(SCAN + "-1", false);
        assertThat(propiedades).containsEntry("indexing", java.util.Map.of("repositories",
                List.of(java.util.Map.of("id", "repo-uagrm"))));
        assertThat(propiedades.get("scanning").toString()).contains("internet=false").contains("repo-uagrm");
    }

    @Test
    void expiracionDelToken() {
        assertThat(CopyleaksCliente.expiracion("2026-11-19T14:00:00.000000Z"))
                .isEqualTo(Instant.parse("2026-11-19T14:00:00Z"));
        assertThat(CopyleaksCliente.expiracion("")).isAfter(Instant.now().plusSeconds(46 * 3600));
        assertThat(CopyleaksCliente.expiracion("basura")).isAfter(Instant.now().plusSeconds(46 * 3600));
    }

    private static SolicitudAnalisis solicitud(boolean internet) {
        return new SolicitudAnalisis(90, 7, 1, SCAN, internet, List.of(
                new SolicitudAnalisis.DocumentoAnalisis(1, "a.pdf", "trabajos/7/a.pdf", "a".repeat(64)),
                new SolicitudAnalisis.DocumentoAnalisis(2, "b.docx", "trabajos/7/b.docx", "b".repeat(64))));
    }

    private static String login(String token, Instant expira) {
        return "{\"access_token\":\"" + token + "\",\".issued\":\"" + Instant.now() + "\",\".expires\":\"" + expira + "\"}";
    }

    static PlagioProperties props(String repositorio) {
        return new PlagioProperties(TipoMotorPlagio.COPYLEAKS,
                new PlagioProperties.Webhook("https://api.uagrm.edu.bo/", TOKEN_WEBHOOK),
                new PlagioProperties.Copyleaks("plagio@uagrm.edu.bo", "00000000-1111-2222-3333-444444444444", true,
                        repositorio, LOGIN, "https://api.copyleaks.com"));
    }
}
