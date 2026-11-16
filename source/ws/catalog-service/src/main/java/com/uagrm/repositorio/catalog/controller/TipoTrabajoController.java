package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.TipoTrabajoRequest;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.TipoTrabajoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/tipos-trabajo")
@RequiredArgsConstructor
public class TipoTrabajoController {

    private final TipoTrabajoService tipoTrabajoService;

    @GetMapping
    public ApiResponse<List<TipoTrabajoResponse>> listar(
            @RequestParam(required = false) Integer idFacultad,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Tipos de trabajo obtenidos", tipoTrabajoService.listar(idFacultad, incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TipoTrabajoResponse> crear(@Valid @RequestBody TipoTrabajoRequest request) {
        return ApiResponse.created("Tipo de trabajo creado", tipoTrabajoService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TipoTrabajoResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody TipoTrabajoRequest request) {
        return ApiResponse.ok("Tipo de trabajo actualizado", tipoTrabajoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TipoTrabajoResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Tipo de trabajo desactivado", tipoTrabajoService.desactivar(id));
    }
}
