package com.uagrm.repositorio.plagio.config;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion de plagiarism-service (infraestructura: variables de entorno, nunca la tabla dominio).
 * Se valida al arrancar: con una configuracion incompleta el servicio NO arranca (mejor que fallar con el primer
 * analisis).
 *
 * @param motor     PLAGIO_MOTOR: SIMULADO o COPYLEAKS (obligatorio fuera del perfil dev)
 * @param webhook   URL publica y token secreto del webhook de Copyleaks
 * @param copyleaks credenciales y opciones de Copyleaks (solo con motor COPYLEAKS)
 */
@Validated
@ConfigurationProperties(prefix = "app.plagio")
public record PlagioProperties(@NotNull TipoMotorPlagio motor,
                               @Valid @NotNull @DefaultValue Webhook webhook,
                               @Valid @NotNull @DefaultValue Copyleaks copyleaks) {

    /** Largo minimo del token del webhook: 32 caracteres aleatorios no se adivinan probando. */
    public static final int LARGO_MINIMO_TOKEN = 32;

    /**
     * @param baseUrl PLAGIO_WEBHOOK_BASE_URL, p. ej. https://api.uagrm.edu.bo (sin "/" final)
     * @param token   PLAGIO_WEBHOOK_TOKEN (Secret); vacio = webhook apagado (siempre 404)
     */
    public record Webhook(@DefaultValue("") String baseUrl, @DefaultValue("") String token) {

        @AssertTrue(message = "PLAGIO_WEBHOOK_TOKEN debe tener al menos 32 caracteres (o estar vacio)")
        public boolean isTokenValido() {
            return token.isEmpty() || token.length() >= LARGO_MINIMO_TOKEN;
        }

        public boolean activo() {
            return !token.isEmpty();
        }

        @Override
        public String toString() {
            return "Webhook[baseUrl=" + baseUrl + ", token=" + (activo() ? "****" : "") + "]";  // sin el secreto
        }
    }

    /**
     * @param email         COPYLEAKS_EMAIL
     * @param apiKey        COPYLEAKS_API_KEY (Secret)
     * @param sandbox       COPYLEAKS_SANDBOX: resultados de prueba sin consumir creditos
     * @param repositorioId COPYLEAKS_REPOSITORIO_ID: Private Cloud Hub para la comparacion interna (opcional)
     * @param loginUrl      URL del login (configurable solo para pruebas)
     * @param apiUrl        URL base de la API (configurable solo para pruebas)
     */
    public record Copyleaks(@DefaultValue("") String email,
                            @DefaultValue("") String apiKey,
                            @DefaultValue("false") boolean sandbox,
                            @DefaultValue("") String repositorioId,
                            @NotBlank @DefaultValue("https://id.copyleaks.com/v3/account/login/api") String loginUrl,
                            @NotBlank @DefaultValue("https://api.copyleaks.com") String apiUrl) {

        @Override
        public String toString() {
            return "Copyleaks[email=" + email + ", sandbox=" + sandbox + ", apiUrl=" + apiUrl + "]";  // sin la API key
        }
    }

    /** Con motor COPYLEAKS son obligatorias las credenciales, la URL publica y el token del webhook. */
    @AssertTrue(message = "Con PLAGIO_MOTOR=COPYLEAKS son obligatorias COPYLEAKS_EMAIL, COPYLEAKS_API_KEY, "
            + "PLAGIO_WEBHOOK_BASE_URL (https) y PLAGIO_WEBHOOK_TOKEN")
    public boolean isCopyleaksCompleto() {
        if (motor != TipoMotorPlagio.COPYLEAKS) {
            return true;
        }
        return !copyleaks.email().isBlank() && !copyleaks.apiKey().isBlank() && webhook.activo()
                && webhook.baseUrl().startsWith("https://");
    }
}
