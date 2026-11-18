package com.uagrm.repositorio.work.config;

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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * work-service no emite tokens: valida los access tokens de auth-service (misma firma HS256, mismo emisor) y la
 * revocacion de Redis (fail-closed: 503 si Redis cae).
 *
 * <p>Aqui se filtra por ROL (primera barrera): crear/editar/subir/enviar solo ESTUDIANTE; revisar/aprobar/rechazar
 * solo DOCENTE; la bandeja DOCENTE o ADMIN; ver y descargar cualquier rol. La relacion con el trabajo concreto
 * (autor, docente asignado al grupo) la verifica el service en cada operacion (segunda barrera), y un trabajo
 * que el usuario no puede ver responde 404. Quien aun debe cambiar su contrasena no tiene rol: 403 en todo.</p>
 */
@Configuration
public class SecurityConfig {

    private static final String TRABAJO = "/api/v1/trabajos/*";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder accessTokenDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            RevocacionTokenService revocacionTokenService,
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
                        .requestMatchers(HttpMethod.PATCH, TRABAJO + "/revisar", TRABAJO + "/aprobar",
                                TRABAJO + "/rechazar").hasRole(docente)
                        .requestMatchers(HttpMethod.GET, TRABAJO, "/api/v1/documentos/*/descarga",
                                "/api/v1/documentos/reglas").hasAnyRole(admin, docente, estudiante)
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
