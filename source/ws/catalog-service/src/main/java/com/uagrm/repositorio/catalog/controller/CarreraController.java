package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.CarreraRequest;
import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.CarreraService;
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
@RequestMapping("/api/v1/carreras")
@RequiredArgsConstructor
public class CarreraController {

    private final CarreraService carreraService;

    @GetMapping
    public ApiResponse<List<CarreraResponse>> listar(
            @RequestParam(required = false) Integer idFacultad,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Carreras obtenidas", carreraService.listar(idFacultad, incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CarreraResponse> crear(@Valid @RequestBody CarreraRequest request) {
        return ApiResponse.created("Carrera creada", carreraService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CarreraResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody CarreraRequest request) {
        return ApiResponse.ok("Carrera actualizada", carreraService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<CarreraResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Carrera desactivada", carreraService.desactivar(id));
    }
}
