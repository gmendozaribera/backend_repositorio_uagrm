package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.notificacion.NotificacionResponse;
import com.uagrm.repositorio.dto.notificacion.NotificacionesPendientesResponse;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notificaciones del usuario autenticado (cualquier rol). El usuario sale siempre del token, nunca de la URL. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public ApiResponse<PaginaResponse<NotificacionResponse>> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ApiResponse.ok("Notificaciones obtenidas", notificacionService.listar(jwt, soloNoLeidas, pagina, tamano));
    }

    @GetMapping("/pendientes")
    public ApiResponse<NotificacionesPendientesResponse> pendientes(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok("Notificaciones pendientes", notificacionService.pendientes(jwt));
    }

    @PatchMapping("/{id}/leida")
    public ApiResponse<NotificacionResponse> marcarLeida(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ApiResponse.ok("Notificacion marcada como leida", notificacionService.marcarLeida(jwt, id));
    }

    @PostMapping("/leidas")
    public ApiResponse<Void> marcarTodasLeidas(@AuthenticationPrincipal Jwt jwt) {
        notificacionService.marcarTodasLeidas(jwt);
        return ApiResponse.ok("Notificaciones marcadas como leidas", null);
    }
}
