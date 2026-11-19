package com.uagrm.repositorio.work.config;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.redis.LimitePublicoService;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.RevocacionTokenFilter;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * work-service no emite tokens: valida los access tokens de auth-service (misma firma HS256, mismo emisor) y la
 * revocacion de Redis (fail-closed: 503 si Redis cae).
 *
 * <p>Aqui se filtra por ROL (primera barrera): crear/editar/subir/enviar solo ESTUDIANTE; revisar/aprobar/rechazar
 * solo DOCENTE; la bandeja DOCENTE o ADMIN; ver, descargar y el repositorio (trabajos EVALUADO) cualquier rol. La relacion con el trabajo concreto
 * (autor, docente asignado al grupo) la verifica el service en cada operacion (segunda barrera), y un trabajo
 * que el usuario no puede ver responde 404. Quien aun debe cambiar su contrasena no tiene rol: 403 en todo.</p>
 *
 * <p>Unica excepcion sin token: GET de /api/v1/publico/repositorio, /publico/repositorio/{id} y /publico/estado
 * (repositorio anonimo, segun ConfigFacultad.accesoExterno). Ver {@link #ignorarTokenEnRutasPublicas()}. Los GET de
 * /api/v1/publico/** tienen un limite por IP ({@link LimitePublicoFilter}).</p>
 */
@Configuration
public class SecurityConfig {

    private static final String TRABAJO = "/api/v1/trabajos/*";
    /** Rutas del repositorio anonimo (work-api: security: []). */
    private static final String[] PUBLICO_RUTAS = {
            "/api/v1/publico/repositorio", "/api/v1/publico/repositorio/*", "/api/v1/publico/estado"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder accessTokenDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            RevocacionTokenService revocacionTokenService,
                                            LimitePublicoService limitePublico,
                                            SeguridadErrorHandler errores) throws Exception {
        String estudiante = TipoUsuario.ESTUDIANTE;
        String docente = TipoUsuario.DOCENTE;
        String admin = TipoUsuario.ADMIN;
        http
                // API stateless con JWT en el header Authorization: sin cookies no hay CSRF que proteger
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        // Orden importa: la primera regla que coincide gana (/mios y /revision antes de /{id})
                        .requestMatchers(HttpMethod.GET, "/api/v1/trabajos/mios").hasRole(estudiante)
                        .requestMatchers(HttpMethod.GET, "/api/v1/trabajos/revision").hasAnyRole(docente, admin)
                        .requestMatchers(HttpMethod.POST, "/api/v1/trabajos").hasRole(estudiante)
                        .requestMatchers(HttpMethod.PUT, TRABAJO).hasRole(estudiante)
                        .requestMatchers(HttpMethod.DELETE, TRABAJO, "/api/v1/documentos/*").hasRole(estudiante)
                        .requestMatchers(HttpMethod.PATCH, TRABAJO + "/enviar").hasRole(estudiante)
                        .requestMatchers(HttpMethod.POST, TRABAJO + "/documentos", TRABAJO + "/enlaces")
                        .hasRole(estudiante)
                        // calificar: DOCENTE aqui; que sea el Administrador de Curso del grupo lo exige el service
                        .requestMatchers(HttpMethod.PATCH, TRABAJO + "/revisar", TRABAJO + "/aprobar",
                                TRABAJO + "/rechazar", TRABAJO + "/calificar").hasRole(docente)
                        .requestMatchers(HttpMethod.GET, TRABAJO, "/api/v1/documentos/*/descarga",
                                "/api/v1/documentos/reglas").hasAnyRole(admin, docente, estudiante)
                        // Repositorio: trabajos EVALUADO, publicos para cualquier rol autenticado
                        .requestMatchers(HttpMethod.GET, "/api/v1/repositorio", "/api/v1/repositorio/*")
                        .hasAnyRole(admin, docente, estudiante)
                        // Acceso anonimo: SOLO lectura (GET) de estas tres rutas. Que facultades se muestran lo
                        // decide el service (ConfigFacultad.accesoExterno). Descargar archivos sigue exigiendo token.
                        .requestMatchers(HttpMethod.GET, PUBLICO_RUTAS).permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(ignorarTokenEnRutasPublicas())
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errores).accessDeniedHandler(errores))
                // Limite por IP de /publico/** antes de todo lo demas: una IP que ya lo supero no gasta ni BD ni token
                .addFilterBefore(new LimitePublicoFilter(limitePublico, errores),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new RevocacionTokenFilter(revocacionTokenService, errores),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * En las rutas publicas se IGNORA el header Authorization: la peticion se atiende siempre como anonima.
     *
     * <p>En terminos simples: es como la vitrina de una biblioteca, que se mira igual con o sin carnet. Si se leyera
     * el token, un token vencido o revocado haria fallar con 401 una pagina que no necesita sesion, y cada visita
     * consultaria Redis (la lista de tokens revocados): si Redis cae, la vitrina tambien caeria (503). Al no leerlo,
     * el filtro de revocacion no tiene nada que revisar y la respuesta es identica para todos (por eso
     * esUsuarioActual es siempre false). El resto de rutas sigue usando el resolver normal.</p>
     */
    static BearerTokenResolver ignorarTokenEnRutasPublicas() {
        DefaultBearerTokenResolver normal = new DefaultBearerTokenResolver();
        RequestMatcher publicas = new OrRequestMatcher(Arrays.stream(PUBLICO_RUTAS)
                .map(ruta -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, ruta))
                .toList());
        return request -> publicas.matches(request) ? null : normal.resolve(request);
    }
}
