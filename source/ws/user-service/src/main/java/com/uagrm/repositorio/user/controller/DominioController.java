package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest;
import com.uagrm.repositorio.dto.dominio.CrearDominioRequest;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import com.uagrm.repositorio.dto.dominio.ReglasDominio;
import com.uagrm.repositorio.imp.service.dominio.DominioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Parametros configurables del sistema (tabla de dominio). Solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/dominios")
@RequiredArgsConstructor
public class DominioController {

    private final DominioService dominioService;

    @GetMapping
    public ApiResponse<List<DominioResponse>> listar(
            @RequestParam(required = false) @Size(min = 1, max = 50)
            @Pattern(regexp = ReglasDominio.PATRON_CODIGO, message = ReglasDominio.MENSAJE_CODIGO) String domainGroup) {
        return ApiResponse.ok("Parametros obtenidos", dominioService.listar(domainGroup));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DominioResponse> crear(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody CrearDominioRequest request) {
        return ApiResponse.created("Parametro creado", dominioService.crear(jwt, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DominioResponse> obtener(@PathVariable Integer id) {
        return ApiResponse.ok("Parametro obtenido", dominioService.obtener(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DominioResponse> actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                   @Valid @RequestBody ActualizarDominioRequest request) {
        return ApiResponse.ok("Parametro actualizado", dominioService.actualizar(jwt, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(@PathVariable Integer id) {
        dominioService.eliminar(id);
        return ApiResponse.ok("Parametro eliminado", null);
    }
}
