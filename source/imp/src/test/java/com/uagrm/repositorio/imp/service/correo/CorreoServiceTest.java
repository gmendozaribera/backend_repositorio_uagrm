package com.uagrm.repositorio.imp.service.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Prueba SMTP contra un servidor de correo falso en memoria (GreenMail), sin red externa. */
class CorreoServiceTest {

    private static final String REMITENTE = "no-responder@uagrm.edu.bo";
    private static final String PASSWORD = "clave-correcta";

    private final ParametrosService parametros = mock(ParametrosService.class);
    private final CorreoService service = new CorreoService(parametros);
    private GreenMail greenMail;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
        configurar("127.0.0.1", greenMail.getSmtp().getPort(), "", ConstantesDominio.SEGURIDAD_NINGUNA, false);
    }

    @AfterEach
    void apagar() {
        greenMail.stop();
    }

    @Test
    void enviaElCorreoDePruebaAunqueSmtpEsteInactivo() throws Exception {
        service.enviarPrueba("admin@uagrm.edu.bo");

        MimeMessage[] recibidos = greenMail.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        assertThat(recibidos[0].getSubject()).isEqualTo("Repositorio Digital UAGRM - correo de prueba");
        assertThat(recibidos[0].getFrom()[0].toString()).isEqualTo(REMITENTE);
    }

    @Test
    void conPasswordSeAutenticaConElCorreoComoUsuario() {
        greenMail.setUser(REMITENTE, REMITENTE, PASSWORD);
        configurar("127.0.0.1", greenMail.getSmtp().getPort(), PASSWORD, ConstantesDominio.SEGURIDAD_NINGUNA, false);

        service.enviarPrueba("admin@uagrm.edu.bo");

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    @Test
    void credencialesRechazadasDan502SinLaContrasena() {
        greenMail.setUser(REMITENTE, REMITENTE, PASSWORD);
        configurar("127.0.0.1", greenMail.getSmtp().getPort(), "clave-incorrecta", ConstantesDominio.SEGURIDAD_NINGUNA, false);

        assertThatThrownBy(() -> service.enviarPrueba("admin@uagrm.edu.bo"))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getMessage()).contains("credenciales rechazadas").doesNotContain("clave-incorrecta");
                });
    }

    @Test
    void servidorQueNoEscuchaDa502ConMotivoLegible() throws Exception {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }
        configurar("127.0.0.1", puertoLibre, "x", ConstantesDominio.SEGURIDAD_NINGUNA, false);

        assertThatThrownBy(() -> service.enviarPrueba("admin@uagrm.edu.bo"))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getTitle()).isEqualTo("Error del servidor de correo");
                    assertThat(ex.getMessage()).isEqualTo("No se pudo conectar a 127.0.0.1:" + puertoLibre
                            + ": conexion rechazada (revise el host y el puerto).");
                });
    }

    @Test
    void sinHostDevuelve400() {
        configurar("", 587, "", ConstantesDominio.SEGURIDAD_STARTTLS, false);

        assertThatThrownBy(() -> service.enviarPrueba("admin@uagrm.edu.bo"))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("HOST");
                });
    }

    @Test
    void lasNotificacionesRespetanActivo() {
        assertThat(service.enviar("admin@uagrm.edu.bo", "Aviso", "texto")).isFalse();
        assertThat(greenMail.getReceivedMessages()).isEmpty();

        configurar("127.0.0.1", greenMail.getSmtp().getPort(), "", ConstantesDominio.SEGURIDAD_NINGUNA, true);
        assertThat(service.enviar("admin@uagrm.edu.bo", "Aviso", "texto")).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    @Test
    void elSenderAplicaSeguridadYTimeouts() {
        JavaMailSenderImpl ssl = service.crearSender(
                new CorreoService.ConfigSmtp("smtp.x.com", 465, REMITENTE, PASSWORD, ConstantesDominio.SEGURIDAD_SSL, true));
        JavaMailSenderImpl starttls = service.crearSender(
                new CorreoService.ConfigSmtp("smtp.x.com", 587, REMITENTE, "", ConstantesDominio.SEGURIDAD_STARTTLS, true));

        assertThat(ssl.getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", true)
                .containsEntry("mail.smtp.auth", true)
                .containsEntry("mail.smtp.connectiontimeout", 15_000)
                .containsEntry("mail.smtp.timeout", 15_000)
                .containsEntry("mail.smtp.writetimeout", 15_000);
        assertThat(ssl.getUsername()).isEqualTo(REMITENTE);
        assertThat(starttls.getJavaMailProperties()).containsEntry("mail.smtp.starttls.enable", true)
                .containsEntry("mail.smtp.starttls.required", true)
                .containsEntry("mail.smtp.auth", false);
        // toString nunca muestra la contrasena (por si alguien loguea la configuracion)
        assertThat(new CorreoService.ConfigSmtp("h", 1, REMITENTE, PASSWORD, "SSL", true).toString())
                .doesNotContain(PASSWORD);
    }

    private void configurar(String host, int puerto, String password, String seguridad, boolean activo) {
        when(parametros.texto(Parametro.SMTP_HOST)).thenReturn(host);
        when(parametros.entero(Parametro.SMTP_PUERTO)).thenReturn(puerto);
        when(parametros.texto(Parametro.SMTP_CORREO)).thenReturn(REMITENTE);
        when(parametros.secreto(Parametro.SMTP_PASSWORD)).thenReturn(password);
        when(parametros.texto(Parametro.SMTP_SEGURIDAD)).thenReturn(seguridad);
        when(parametros.booleano(Parametro.SMTP_ACTIVO)).thenReturn(activo);
    }
}
