package com.uagrm.repositorio.plagio.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.plagio.copyleaks.WebhookCopyleaksService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Avisos de Copyleaks (sin sesion). Toda la verificacion (token secreto, scan conocido, idempotencia) la hace el
 * service. Nunca se loguea la URL (lleva el token) ni el cuerpo.
 */
@RestController
@RequiredArgsConstructor
public class WebhookCopyleaksController {

    private final WebhookCopyleaksService webhookService;

    @PostMapping("/api/v1/plagio/webhook/copyleaks/{token}/{evento}/{scanId}")
    public ApiResponse<Void> recibir(@PathVariable String token, @PathVariable String evento,
                                     @PathVariable @Size(max = 100) String scanId,
                                     @RequestBody JsonNode cuerpo) {
        webhookService.procesar(token, evento, scanId, cuerpo);
        return ApiResponse.ok("Aviso recibido", null);
    }
}
