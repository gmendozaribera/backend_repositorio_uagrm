package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.perfil.Preferencias;
import com.uagrm.repositorio.imp.service.perfil.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferencias del usuario autenticado (cualquier rol). El usuario sale siempre del token. */
@RestController
@RequestMapping("/api/v1/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping("/preferencias")
    public ApiResponse<Preferencias> obtenerPreferencias(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok("Preferencias obtenidas", perfilService.obtenerPreferencias(jwt));
    }

    @PutMapping("/preferencias")
    public ApiResponse<Preferencias> actualizarPreferencias(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody Preferencias preferencias) {
        return ApiResponse.ok("Preferencias guardadas", perfilService.actualizarPreferencias(jwt, preferencias));
    }
}
