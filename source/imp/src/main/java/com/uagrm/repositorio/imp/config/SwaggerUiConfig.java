package com.uagrm.repositorio.imp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Swagger UI de cada microservicio mostrando su CONTRATO (spec/openapi/&lt;servicio&gt;-api.yaml), no un documento
 * generado desde el codigo: somos contract-first y el YAML es la fuente de verdad.
 *
 * <p>Desactivado por defecto: solo existe con {@code app.swagger-ui.enabled=true} (variable SWAGGER_UI_ENABLED;
 * el perfil dev la pone en true). Activado publica, solo por GET y sin sesion:</p>
 * <ul>
 *   <li>{@code /openapi/<servicio>-api.yaml}: el contrato, empaquetado desde el modulo spec. Solo el del servicio
 *       ({@code app.swagger-ui.contrato}); los demas YAML del classpath no se exponen.</li>
 *   <li>{@code /swagger-ui.html} (redirige) y {@code /swagger-ui/**}: los archivos del webjar de swagger-ui y un
 *       {@code swagger-initializer.js} propio que apunta al YAML.</li>
 * </ul>
 *
 * <p>Por que no springdoc: con {@code springdoc.api-docs.enabled=false} (no generar nada desde el codigo) springdoc
 * apaga tambien su Swagger UI ({@code SwaggerConfig} depende de {@code SpringDocConfiguration}); se usa el webjar
 * oficial de swagger-ui y esta configuracion minima.</p>
 *
 * <p>Seguridad en simple: el contrato no tiene secretos (es lo mismo que recibe el frontend), pero describe toda la
 * API, asi que en produccion se deja apagado. Cuando esta apagado nada de esto existe y esas rutas caen en el
 * {@code denyAll()} del SecurityFilterChain del servicio (401). Cuando esta encendido, una cadena de seguridad
 * propia (que se evalua primero) deja pasar SOLO estas rutas de lectura; los endpoints de la API siguen pidiendo
 * token aunque se prueben desde la UI ("Authorize" con el Bearer del login).</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("app.swagger-ui.enabled")
public class SwaggerUiConfig implements WebMvcConfigurer {

    private static final String WEBJAR_POM = "META-INF/maven/org.webjars/swagger-ui/pom.properties";

    private final String contrato;

    public SwaggerUiConfig(@Value("${app.swagger-ui.contrato}") String contrato) {
        if (!contrato.matches("[a-z-]+-api\\.yaml")) {
            throw new IllegalStateException("app.swagger-ui.contrato debe ser el nombre de un YAML de spec/openapi: "
                    + contrato);
        }
        this.contrato = contrato;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/" + versionWebjar() + "/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui.html", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
    }

    /**
     * El contrato de este servicio (y solo ese): ruta exacta, el nombre no sale de la URL. Ademas reemplaza el
     * swagger-initializer.js del webjar (que apunta al ejemplo petstore) por uno que carga el contrato. Las
     * RouterFunction se resuelven antes que los recursos estaticos. URL relativa: funciona detras de un proxy con
     * prefijo.
     */
    @Bean
    RouterFunction<ServerResponse> swaggerUiRutas() {
        ClassPathResource yaml = new ClassPathResource("openapi/" + contrato);
        if (!yaml.exists()) {
            throw new IllegalStateException("No esta en el classpath el contrato openapi/" + contrato
                    + " (falta runtimeOnly project(':spec'))");
        }
        String js = """
                window.onload = function () {
                  window.ui = SwaggerUIBundle({
                    url: '../openapi/%s',
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
                    layout: 'StandaloneLayout'
                  });
                };
                """.formatted(contrato);
        MediaType javascript = new MediaType("text", "javascript");
        MediaType tipoYaml = new MediaType("application", "yaml", StandardCharsets.UTF_8);
        return RouterFunctions.route()
                .GET("/openapi/" + contrato, request -> ServerResponse.ok().contentType(tipoYaml).body(yaml))
                .GET("/swagger-ui/swagger-initializer.js",
                        request -> ServerResponse.ok().contentType(javascript).body(js))
                .build();
    }

    /** Cadena propia, evaluada antes que la del servicio: solo GET de la UI y del contrato, sin token. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain swaggerUiFilterChain(HttpSecurity http) throws Exception {
        List<RequestMatcher> rutas = List.of("/swagger-ui.html", "/swagger-ui", "/swagger-ui/**", "/openapi/" + contrato)
                .stream()
                .map(ruta -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ruta))
                .toList();
        http.securityMatcher(new OrRequestMatcher(rutas))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** Version del webjar en el classpath (asi la ruta no se desalinea al subir la version en Gradle). */
    static String versionWebjar() {
        try (InputStream in = SwaggerUiConfig.class.getClassLoader().getResourceAsStream(WEBJAR_POM)) {
            if (in == null) {
                throw new IllegalStateException("Swagger UI activado pero falta el webjar org.webjars:swagger-ui");
            }
            Properties pom = new Properties();
            pom.load(in);
            return pom.getProperty("version");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la version del webjar de swagger-ui", e);
        }
    }
}
