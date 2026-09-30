package com.uagrm.repositorio.auth.controller;

import com.uagrm.repositorio.dto.auth.CambioPasswordRequest;
import com.uagrm.repositorio.dto.auth.LoginRequest;
import com.uagrm.repositorio.dto.auth.LoginResponse;
import com.uagrm.repositorio.dto.auth.LogoutRequest;
import com.uagrm.repositorio.dto.auth.RefreshRequest;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse respuesta = authService.login(request);
        String mensaje = respuesta.requiereDosFactores()
                ? "Ingrese el codigo de su aplicacion autenticadora"
                : "Inicio de sesion exitoso";
        return ApiResponse.ok(mensaje, respuesta);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Sesion renovada", authService.refrescar(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt,
                                    @RequestBody(required = false) LogoutRequest request) {
        authService.logout(jwt, request);
        return ApiResponse.ok("Sesion cerrada", null);
    }

    @PostMapping("/password/change")
    public ApiResponse<TokenResponse> cambiarPassword(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody CambioPasswordRequest request) {
        return ApiResponse.ok("Contrasena actualizada. Las demas sesiones fueron cerradas.",
                authService.cambiarPassword(jwt, request));
    }
}
