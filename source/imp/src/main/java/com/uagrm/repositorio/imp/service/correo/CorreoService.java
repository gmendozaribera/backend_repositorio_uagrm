package com.uagrm.repositorio.imp.service.correo;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Correo saliente del sistema con la configuracion del grupo SMTP_GROUP de la tabla de dominio.
 *
 * <p>No hay un JavaMailSender fijo: cada envio arma uno con los parametros vigentes (cacheados en Redis), asi un
 * cambio hecho en /api/v1/dominios aplica al instante sin reiniciar. La contrasena se descifra solo en memoria
 * y nunca se loguea ni se incluye en un mensaje de error.</p>
 *
 * <ul>
 *   <li>{@link #enviarPrueba(String)}: POST /api/v1/config/smtp/prueba. Funciona aunque ACTIVO sea false.</li>
 *   <li>{@link #enviar(String, String, String)}: para las notificaciones de sprints futuros; respeta ACTIVO.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorreoService {

    public static final String ASUNTO_PRUEBA = "Repositorio Digital UAGRM - correo de prueba";

    /** Espera maxima para conectar, leer y escribir (el contrato promete 15 s). */
    static final int TIMEOUT_MS = 15_000;

    private static final String TEXTO_PRUEBA = """
            Este es un correo de prueba del Repositorio Digital UAGRM.

            Si lo recibio, la configuracion del servidor de correo (SMTP) es correcta.
            No responda a este mensaje.""";

    private final ParametrosService parametros;

    /**
     * Configuracion SMTP vigente.
     *
     * @param password contrasena DESCIFRADA ("" si no tiene); toString() la oculta para que nunca llegue a un log
     */
    public record ConfigSmtp(String host, int puerto, String correo, String password, String seguridad, boolean activo) {

        @Override
        public String toString() {
            return "ConfigSmtp[host=" + host + ", puerto=" + puerto + ", correo=" + correo + ", password=***"
                    + ", seguridad=" + seguridad + ", activo=" + activo + "]";
        }
    }

    /** Lee SMTP_GROUP (con cache). 400 si la contrasena guardada no se puede descifrar (la llave cambio). */
    public ConfigSmtp configActual() {
        String password;
        try {
            password = parametros.secreto(Parametro.SMTP_PASSWORD);
        } catch (IllegalStateException ex) {
            log.warn("No se pudo leer SMTP_GROUP.PASSWORD: {}", ex.getMessage());
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Configuracion de correo invalida",
                    "La contrasena SMTP guardada no se puede descifrar; vuelva a guardarla en SMTP_GROUP.PASSWORD.");
        }
        return new ConfigSmtp(
                parametros.texto(Parametro.SMTP_HOST),
                parametros.entero(Parametro.SMTP_PUERTO),
                parametros.texto(Parametro.SMTP_CORREO),
                password,
                parametros.texto(Parametro.SMTP_SEGURIDAD),
                parametros.booleano(Parametro.SMTP_ACTIVO));
    }

    /**
     * Fabrica del sender: SSL → conexion cifrada desde el inicio (mail.smtp.ssl.enable); STARTTLS → conexion que
     * se cifra antes de autenticar y falla si el servidor no lo soporta (starttls.required), asi la contrasena
     * nunca viaja en texto plano; NINGUNA → sin cifrado (solo para servidores internos). Con contrasena se
     * autentica con CORREO como usuario. Timeouts de 15 s.
     */
    public JavaMailSenderImpl crearSender(ConfigSmtp config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.puerto());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        boolean conPassword = !config.password().isEmpty();
        props.put("mail.smtp.auth", conPassword);
        if (conPassword) {
            sender.setUsername(config.correo());
            sender.setPassword(config.password());
        }
        switch (config.seguridad()) {
            case ConstantesDominio.SEGURIDAD_SSL -> {
                props.put("mail.smtp.ssl.enable", true);
                props.put("mail.smtp.ssl.checkserveridentity", true);
            }
            case ConstantesDominio.SEGURIDAD_STARTTLS -> {
                props.put("mail.smtp.starttls.enable", true);
                props.put("mail.smtp.starttls.required", true);
                props.put("mail.smtp.ssl.checkserveridentity", true);
            }
            default -> {
                // NINGUNA: sin cifrado
            }
        }
        return sender;
    }

    /**
     * Envia el correo de prueba con la configuracion actual (aunque ACTIVO sea false).
     * 400 si falta HOST, PUERTO o CORREO; 502 con un motivo legible si el servidor rechaza conexion,
     * autenticacion o envio.
     */
    public void enviarPrueba(String destinatario) {
        ConfigSmtp config = configActual();
        List<String> faltan = new ArrayList<>();
        if (config.host().isBlank()) {
            faltan.add(ConstantesDominio.HOST);
        }
        if (config.puerto() <= 0) {
            faltan.add(ConstantesDominio.PUERTO);
        }
        if (config.correo().isBlank()) {
            faltan.add(ConstantesDominio.CORREO);
        }
        if (!faltan.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Configuracion de correo incompleta",
                    "Falta configurar " + String.join(", ", faltan) + " del grupo " + ConstantesDominio.GRUPO_SMTP
                            + " en /api/v1/dominios.");
        }
        try {
            enviar(config, destinatario, ASUNTO_PRUEBA, TEXTO_PRUEBA);
        } catch (MailException ex) {
            String motivo = motivo(config, ex);
            log.warn("Prueba SMTP fallida contra {}:{} ({}): {}", config.host(), config.puerto(),
                    ex.getClass().getSimpleName(), motivo);
            throw new NegocioException(HttpStatus.BAD_GATEWAY, "Error del servidor de correo", motivo);
        }
    }

    /**
     * Envio para notificaciones del sistema. Si ACTIVO es false no envia nada (false). Un fallo del servidor de
     * correo no debe romper la operacion de negocio que lo origino: se loguea y devuelve false.
     */
    public boolean enviar(String destinatario, String asunto, String texto) {
        ConfigSmtp config = configActual();
        if (!config.activo() || config.host().isBlank()) {
            log.info("Correo no enviado ('{}'): SMTP inactivo o sin configurar", asunto);
            return false;
        }
        try {
            enviar(config, destinatario, asunto, texto);
            return true;
        } catch (MailException ex) {
            log.warn("No se pudo enviar el correo '{}': {}", asunto, motivo(config, ex));
            return false;
        }
    }

    private void enviar(ConfigSmtp config, String destinatario, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(config.correo());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        crearSender(config).send(mensaje);
    }

    /**
     * Motivo legible para el administrador, armado SOLO con textos propios + host:puerto (nunca el mensaje crudo
     * de JavaMail, que podria incluir datos de la sesion; nunca la contrasena).
     */
    static String motivo(ConfigSmtp config, MailException ex) {
        String servidor = config.host() + ":" + config.puerto();
        if (ex instanceof MailAuthenticationException || causa(ex, AuthenticationFailedException.class)) {
            return "No se pudo autenticar en " + servidor + ": credenciales rechazadas.";
        }
        if (causa(ex, UnknownHostException.class)) {
            return "No se pudo conectar a " + servidor + ": el servidor no existe o no se puede resolver su nombre.";
        }
        if (causa(ex, SocketTimeoutException.class)) {
            return "No se pudo conectar a " + servidor + ": el servidor no respondio en " + TIMEOUT_MS / 1000 + " segundos.";
        }
        if (causa(ex, ConnectException.class)) {
            return "No se pudo conectar a " + servidor + ": conexion rechazada (revise el host y el puerto).";
        }
        if (causa(ex, SSLException.class)) {
            return "No se pudo establecer una conexion segura con " + servidor
                    + ": revise SEGURIDAD (SSL suele usar el puerto 465 y STARTTLS el 587).";
        }
        if (causa(ex, SendFailedException.class)) {
            return "El servidor " + servidor + " rechazo el envio (remitente o destinatario no permitido).";
        }
        return "No se pudo enviar el correo por " + servidor + ".";
    }

    /** true si alguna excepcion de la cadena (incluidas las anidadas de JavaMail) es del tipo indicado. */
    private static boolean causa(Throwable ex, Class<? extends Throwable> tipo) {
        // En Jakarta Mail getCause() devuelve tambien la "next exception" de MessagingException
        for (Throwable actual = ex; actual != null; actual = actual.getCause()) {
            if (tipo.isInstance(actual)) {
                return true;
            }
        }
        if (ex instanceof MailSendException envio) {
            for (Exception fallo : envio.getFailedMessages().values()) {
                if (causa(fallo, tipo)) {
                    return true;
                }
            }
        }
        return false;
    }
}
