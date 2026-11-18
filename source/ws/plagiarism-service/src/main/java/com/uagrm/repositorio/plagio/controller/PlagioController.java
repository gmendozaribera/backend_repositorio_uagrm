package com.uagrm.repositorio.plagio.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.plagio.AnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoAnalisisPlagioResponse;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Analisis de plagio de un trabajo. El usuario sale siempre del token; los permisos los decide el service. */
@RestController
@RequestMapping("/api/v1/plagio/trabajos")
@RequiredArgsConstructor
public class PlagioController {

    private final PlagioService plagioService;

    @PostMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AnalisisPlagioResponse> iniciar(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable @Positive Integer id) {
        return ApiResponse.of(HttpStatus.ACCEPTED, "Analisis de plagio iniciado", plagioService.iniciar(jwt, id));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnalisisPlagioResponse> ultimo(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Analisis de plagio obtenido", plagioService.ultimo(jwt, id));
    }

    @GetMapping("/{id}/estado")
    public ApiResponse<EstadoAnalisisPlagioResponse> estado(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Estado del analisis obtenido", plagioService.estado(jwt, id));
    }

    @GetMapping("/{id}/historial")
    public ApiResponse<List<AnalisisPlagioResponse>> historial(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Historial de analisis obtenido", plagioService.historial(jwt, id));
    }
}
