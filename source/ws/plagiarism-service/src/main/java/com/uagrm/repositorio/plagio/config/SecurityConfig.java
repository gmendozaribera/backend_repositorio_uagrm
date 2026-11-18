package com.uagrm.repositorio.plagio.config;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.RevocacionTokenFilter;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
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
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * plagiarism-service no emite tokens: valida los access tokens de auth-service (misma firma HS256, mismo emisor) y
 * la revocacion de Redis (fail-closed: 503 si Redis cae).
 *
 * <p>Primera barrera, por ROL: todo /api/v1/plagio/trabajos/** solo DOCENTE o ADMIN (un estudiante recibe 403).
 * La segunda barrera la pone el service: el docente debe estar asignado al grupo del trabajo (si no, 404).</p>
 *
 * <p>Unica excepcion sin sesion: el webhook de Copyleaks (POST /api/v1/plagio/webhook/copyleaks/**). Ver
 * {@link #ignorarTokenEnWebhook()}.</p>
 */
@Configuration
public class SecurityConfig {

    static final String WEBHOOK = "/api/v1/plagio/webhook/copyleaks/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder accessTokenDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            RevocacionTokenService revocacionTokenService,
                                            SeguridadErrorHandler errores) throws Exception {
        String docente = TipoUsuario.DOCENTE;
        String admin = TipoUsuario.ADMIN;
        http
                // API stateless con JWT en el header Authorization: sin cookies no hay CSRF que proteger
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/plagio/trabajos/*").hasAnyRole(docente, admin)
                        .requestMatchers(HttpMethod.GET, "/api/v1/plagio/trabajos/*", "/api/v1/plagio/trabajos/*/estado",
                                "/api/v1/plagio/trabajos/*/historial").hasAnyRole(docente, admin)
                        // Lo llama Copyleaks, que no tiene sesion: se autentica con el token secreto de la URL, que
                        // verifica el service (token incorrecto -> 404). Solo POST.
                        .requestMatchers(HttpMethod.POST, WEBHOOK).permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(ignorarTokenEnWebhook())
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errores).accessDeniedHandler(errores))
                .addFilterAfter(new RevocacionTokenFilter(revocacionTokenService, errores),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * En el webhook se IGNORA el header Authorization: Copyleaks no manda token de sesion, y si alguien mandara uno
     * vencido no debe convertir el aviso en un 401. Al no leerlo tampoco se consulta Redis (la lista de sesiones
     * cerradas): un Redis caido no hace perder los avisos de Copyleaks. En terminos simples: es el buzon de la
     * puerta de calle; no pide carnet, pero solo acepta sobres que traen la clave correcta (el token de la URL).
     */
    static BearerTokenResolver ignorarTokenEnWebhook() {
        DefaultBearerTokenResolver normal = new DefaultBearerTokenResolver();
        RequestMatcher webhook = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, WEBHOOK);
        return request -> webhook.matches(request) ? null : normal.resolve(request);
    }
}
