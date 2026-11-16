package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.ConfigFacultadRequest;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuracion por facultad. GET: cualquier usuario autenticado; PUT: solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/facultades/{id}/config")
@RequiredArgsConstructor
public class ConfigFacultadController {

    private final ConfigFacultadService configFacultadService;

    @GetMapping
    public ApiResponse<ConfigFacultadResponse> obtener(@PathVariable Integer id) {
        return ApiResponse.ok("Configuracion obtenida", configFacultadService.obtener(id));
    }

    @PutMapping
    public ApiResponse<ConfigFacultadResponse> actualizar(@PathVariable Integer id,
                                                          @Valid @RequestBody ConfigFacultadRequest request) {
        return ApiResponse.ok("Configuracion guardada", configFacultadService.actualizar(id, request));
    }
}
