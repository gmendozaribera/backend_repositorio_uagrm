package com.uagrm.repositorio.work.controller;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.AprobarRequest;
import com.uagrm.repositorio.dto.trabajo.RechazarRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResumenResponse;
import com.uagrm.repositorio.imp.service.trabajo.TrabajoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Trabajos y su revision. El usuario sale siempre del token; los permisos sobre cada trabajo los decide el service. */
@RestController
@RequestMapping("/api/v1/trabajos")
@RequiredArgsConstructor
public class TrabajoController {

    private final TrabajoService trabajoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TrabajoResponse> crear(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody TrabajoRequest request) {
        return ApiResponse.created("Trabajo creado", trabajoService.crear(jwt, request));
    }

    @GetMapping("/mios")
    public ApiResponse<PaginaResponse<TrabajoResumenResponse>> listarMios(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoTrabajo estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ApiResponse.ok("Trabajos obtenidos", trabajoService.listarMios(jwt, estado, pagina, tamano));
    }

    @GetMapping("/revision")
    public ApiResponse<PaginaResponse<TrabajoResumenResponse>> listarRevision(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoTrabajo estado,
            @RequestParam(required = false) @Positive Integer idGrupo,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ApiResponse.ok("Trabajos por revisar obtenidos",
                trabajoService.listarRevision(jwt, estado, idGrupo, pagina, tamano));
    }

    @GetMapping("/{id}")
    public ApiResponse<TrabajoResponse> obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Trabajo obtenido", trabajoService.obtener(jwt, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TrabajoResponse> actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id,
                                                   @Valid @RequestBody TrabajoRequest request) {
        return ApiResponse.ok("Trabajo actualizado", trabajoService.actualizar(jwt, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id) {
        trabajoService.eliminar(jwt, id);
        return ApiResponse.ok("Trabajo eliminado", null);
    }

    @PatchMapping("/{id}/enviar")
    public ApiResponse<TrabajoResponse> enviar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Trabajo enviado a revision", trabajoService.enviar(jwt, id));
    }

    @PatchMapping("/{id}/revisar")
    public ApiResponse<TrabajoResponse> tomarRevision(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Trabajo en revision", trabajoService.tomarRevision(jwt, id));
    }

    @PatchMapping("/{id}/aprobar")
    public ApiResponse<TrabajoResponse> aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id,
                                                @Valid @RequestBody AprobarRequest request) {
        return ApiResponse.ok("Trabajo aprobado", trabajoService.aprobar(jwt, id, request));
    }

    @PatchMapping("/{id}/rechazar")
    public ApiResponse<TrabajoResponse> rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id,
                                                 @Valid @RequestBody RechazarRequest request) {
        return ApiResponse.ok("Trabajo rechazado", trabajoService.rechazar(jwt, id, request));
    }
}
