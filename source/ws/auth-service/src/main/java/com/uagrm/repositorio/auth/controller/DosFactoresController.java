package com.uagrm.repositorio.auth.controller;

import com.uagrm.repositorio.dto.auth.ActivarDosFactoresResponse;
import com.uagrm.repositorio.dto.auth.CodigoTotpRequest;
import com.uagrm.repositorio.dto.auth.TokenResponse;
import com.uagrm.repositorio.dto.auth.VerificarDosFactoresRequest;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.AuthService;
import com.uagrm.repositorio.imp.service.DosFactoresService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
public class DosFactoresController {

    private final AuthService authService;
    private final DosFactoresService dosFactoresService;

    /** Paso 2 del login (publico): token temporal + codigo TOTP → JWT. */
    @PostMapping("/verify")
    public ApiResponse<TokenResponse> verificar(@Valid @RequestBody VerificarDosFactoresRequest request) {
        return ApiResponse.ok("Inicio de sesion exitoso", authService.verificarDosFactores(request));
    }

    /** Perfil: genera secreto + QR. 2FA queda inactivo hasta /confirm. */
    @PostMapping("/enable")
    public ApiResponse<ActivarDosFactoresResponse> activar(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok("Escanee el codigo QR y confirme con un codigo",
                dosFactoresService.iniciarActivacion(jwt));
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirmar(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CodigoTotpRequest request) {
        dosFactoresService.confirmarActivacion(jwt, request);
        return ApiResponse.ok("Verificacion en dos pasos activada", null);
    }

    @PostMapping("/disable")
    public ApiResponse<Void> desactivar(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CodigoTotpRequest request) {
        dosFactoresService.desactivar(jwt, request);
        return ApiResponse.ok("Verificacion en dos pasos desactivada", null);
    }
}
