package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.dominio.PruebaSmtpRequest;
import com.uagrm.repositorio.imp.service.correo.CorreoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prueba de la configuracion de correo saliente (SMTP_GROUP de la tabla dominio). Solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/config/smtp")
@RequiredArgsConstructor
public class ConfigSmtpController {

    private final CorreoService correoService;

    @PostMapping("/prueba")
    public ApiResponse<Void> probar(@Valid @RequestBody PruebaSmtpRequest request) {
        correoService.enviarPrueba(request.destinatario());
        return ApiResponse.ok("Correo de prueba enviado a " + request.destinatario(), null);
    }
}
