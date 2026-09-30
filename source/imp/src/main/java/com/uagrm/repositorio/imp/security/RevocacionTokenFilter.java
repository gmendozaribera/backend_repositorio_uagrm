package com.uagrm.repositorio.imp.security;

import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Corre despues de BearerTokenAuthenticationFilter (firma y expiracion ya validadas) y rechaza
 * los tokens revocados en Redis. Si Redis no responde → 503 (fail-closed).
 * No es un @Component: se registra solo dentro del SecurityFilterChain de cada microservicio.
 */
@RequiredArgsConstructor
public class RevocacionTokenFilter extends OncePerRequestFilter {

    private final RevocacionTokenService revocacionTokenService;
    private final SeguridadErrorHandler errores;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            try {
                if (revocacionTokenService.estaRevocado(jwtAuth.getToken())) {
                    SecurityContextHolder.clearContext();
                    errores.commence(request, response, new TokenRevocadoException());
                    return;
                }
            } catch (NegocioException ex) {
                SecurityContextHolder.clearContext();
                errores.resolver(request, response, ex);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
