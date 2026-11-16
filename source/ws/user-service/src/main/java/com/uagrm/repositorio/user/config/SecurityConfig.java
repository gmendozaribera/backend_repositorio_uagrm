package com.uagrm.repositorio.user.config;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.RevocacionTokenFilter;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * user-service no emite tokens: valida los access tokens de auth-service (misma firma HS256, mismo emisor)
 * y consulta la blacklist de Redis (fail-closed: 503 si Redis cae). La API es solo ADMIN, salvo
 * /api/v1/notificaciones/** y /api/v1/perfil/**, que puede usar cualquier rol porque solo operan sobre los datos
 * del propio usuario del token (nunca reciben un id de usuario). Quien aun tiene la contrasena pendiente de
 * cambio solo tiene la autoridad PASSWORD_CHANGE_REQUIRED (ningun rol) y recibe 403 en todo.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder accessTokenDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            RevocacionTokenService revocacionTokenService,
                                            SeguridadErrorHandler errores) throws Exception {
        http
                // API stateless con JWT en el header Authorization: sin cookies no hay CSRF que proteger
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        // Orden importa: la primera regla que coincide gana
                        .requestMatchers("/api/v1/notificaciones/**", "/api/v1/perfil/**")
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
