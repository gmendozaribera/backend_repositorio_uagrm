package com.uagrm.repositorio.catalog.config;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.CabecerasSeguridad;
import com.uagrm.repositorio.imp.security.RevocacionTokenFilter;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * catalog-service no emite tokens: solo valida los access tokens de auth-service (misma firma HS256,
 * mismo emisor) y consulta la blacklist de Redis igual que auth-service (fail-closed: 503 si Redis cae).
 * Lecturas: cualquier rol. Escrituras: solo ADMIN. Quien aun tiene la password_default no entra a nada.
 *
 * <p>Excepcion (catalog-api 1.3.0): los LISTADOS de {@link #LISTADOS_PUBLICOS} tambien se leen sin token, porque el
 * repositorio publico los usa para armar sus filtros (facultad, carrera, materia...). En terminos simples: es la
 * lista de pasillos y estantes de la biblioteca, que cualquiera puede ver en la entrada; no contienen datos de
 * personas ni configuracion. Como funciona:</p>
 * <ul>
 *   <li>Sin header Authorization: la peticion es anonima y el filtro de revocacion no tiene nada que revisar (no
 *       consulta Redis), asi que tampoco depende de Redis.</li>
 *   <li>Con un token valido: se valida igual que siempre (firma, vencimiento y revocacion en Redis) y aplican las
 *       mismas reglas que antes: quien aun debe cambiar su contrasena (sin rol) sigue recibiendo 403. Por eso la
 *       regla es "anonimo O con rol de lectura" y no un permitAll.</li>
 *   <li>Con un token invalido, vencido o revocado: 401 (no se "degrada" a anonimo: el cliente se entera de que su
 *       sesion ya no sirve, como en el resto de la API).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /** GET sin token (catalog-api: {@code security: [{}, bearerAuth]}). Rutas exactas: sin {id} ni hijos. */
    static final String[] LISTADOS_PUBLICOS = {"/api/v1/facultades", "/api/v1/carreras", "/api/v1/materias",
            "/api/v1/semestres", "/api/v1/grupos", "/api/v1/fases", "/api/v1/tipos-trabajo"};
    private static final String[] LECTORES = {TipoUsuario.ADMIN, TipoUsuario.DOCENTE, TipoUsuario.ESTUDIANTE};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder accessTokenDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            RevocacionTokenService revocacionTokenService,
                                            SeguridadErrorHandler errores,
                                            CabecerasSeguridad cabeceras) throws Exception {
        http
                // CSRF apagado A PROPOSITO, y es seguro: CSRF es el ataque en que otra pagina hace que el navegador
                // envie una peticion "con la sesion del usuario" porque el navegador adjunta solo las cookies. Aqui
                // no hay cookies ni sesion: el JWT viaja en la cabecera Authorization, que solo agrega el codigo del
                // frontend; una pagina ajena no puede ponerla (y CORS le impide leer la respuesta).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // nosniff, DENY, no-referrer, no-store, CSP y HSTS (si SEGURIDAD_HSTS_ENABLED): ver CabecerasSeguridad
                .headers(cabeceras.api())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator: solo health (liveness/readiness de Kubernetes), sin detalles
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // Configuracion por facultad: la lee cualquier rol (p. ej. el limite de tamano de archivo),
                        // solo el ADMIN la cambia. Explicito para que una regla futura no la abra por error.
                        .requestMatchers(HttpMethod.GET, "/api/v1/facultades/*/config")
                        .hasAnyRole(TipoUsuario.ADMIN, TipoUsuario.DOCENTE, TipoUsuario.ESTUDIANTE)
                        .requestMatchers("/api/v1/facultades/*/config").hasRole(TipoUsuario.ADMIN)
                        // Listados de solo lectura SIN sesion (filtros del repositorio publico): solo GET y solo
                        // estas rutas exactas; /grupos/{id}/docentes, /facultades/{id}/config y toda escritura
                        // siguen exigiendo token. Ver LISTADOS_PUBLICOS.
                        .requestMatchers(HttpMethod.GET, LISTADOS_PUBLICOS).access(AuthorizationManagers.anyOf(
                                AuthenticatedAuthorizationManager.anonymous(),
                                AuthorityAuthorizationManager.hasAnyRole(LECTORES)))
                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                        .hasAnyRole(TipoUsuario.ADMIN, TipoUsuario.DOCENTE, TipoUsuario.ESTUDIANTE)
                        .requestMatchers("/api/v1/**").hasRole(TipoUsuario.ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errores).accessDeniedHandler(errores))
                .addFilterAfter(new RevocacionTokenFilter(revocacionTokenService, errores),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
