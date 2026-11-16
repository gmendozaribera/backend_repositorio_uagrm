package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.FacultadRequest;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.FacultadService;
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
@RequestMapping("/api/v1/facultades")
@RequiredArgsConstructor
public class FacultadController {

    private final FacultadService facultadService;

    @GetMapping
    public ApiResponse<List<FacultadResponse>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Facultades obtenidas", facultadService.listar(incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FacultadResponse> crear(@Valid @RequestBody FacultadRequest request) {
        return ApiResponse.created("Facultad creada", facultadService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FacultadResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody FacultadRequest request) {
        return ApiResponse.ok("Facultad actualizada", facultadService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<FacultadResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Facultad desactivada", facultadService.desactivar(id));
    }
}
