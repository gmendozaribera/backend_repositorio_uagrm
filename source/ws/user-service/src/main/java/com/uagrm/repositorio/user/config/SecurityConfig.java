package com.uagrm.repositorio.user.config;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.security.CabecerasSeguridad;
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
