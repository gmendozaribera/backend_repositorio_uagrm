package com.uagrm.repositorio.work.config;

import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.redis.LimitePublicoService;
import com.uagrm.repositorio.imp.security.SeguridadErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite por IP de las consultas anonimas: SOLO cuenta los GET de /api/v1/publico/**; cualquier otra ruta (las
 * autenticadas) pasa sin tocar Redis. Al superarlo responde 429 ProblemDetail con Retry-After (via
 * GlobalExceptionHandler) sin llegar al controller.
 *
 * <p>La IP es request.getRemoteAddr(): con server.forward-headers-strategy=native Tomcat la toma de
 * X-Forwarded-For solo si el request viene de un proxy interno (nginx), asi un visitante no puede cambiar de "IP"
 * inventando la cabecera para saltarse el limite.</p>
 *
 * <p>No es un @Component: se registra solo dentro del SecurityFilterChain (si fuera un bean, Spring Boot ademas lo
 * registraria como filtro del servlet y contaria dos veces).</p>
 */
@RequiredArgsConstructor
public class LimitePublicoFilter extends OncePerRequestFilter {

    static final RequestMatcher RUTAS_PUBLICAS =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/publico/**");

    private final LimitePublicoService limite;
    private final SeguridadErrorHandler errores;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RUTAS_PUBLICAS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            limite.registrar(request.getRemoteAddr());
        } catch (LimiteIntentosException ex) {
            errores.resolver(request, response, ex);
            return;
        }
        chain.doFilter(request, response);
    }
}
