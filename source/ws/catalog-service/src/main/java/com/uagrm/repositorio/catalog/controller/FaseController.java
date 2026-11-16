package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.FaseRequest;
import com.uagrm.repositorio.dto.catalogo.FaseResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.FaseService;
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
@RequestMapping("/api/v1/fases")
@RequiredArgsConstructor
public class FaseController {

    private final FaseService faseService;

    @GetMapping
    public ApiResponse<List<FaseResponse>> listar(
            @RequestParam(required = false) Integer idFacultad,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Fases obtenidas", faseService.listar(idFacultad, incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FaseResponse> crear(@Valid @RequestBody FaseRequest request) {
        return ApiResponse.created("Fase creada", faseService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FaseResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody FaseRequest request) {
        return ApiResponse.ok("Fase actualizada", faseService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<FaseResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Fase desactivada", faseService.desactivar(id));
    }
}
