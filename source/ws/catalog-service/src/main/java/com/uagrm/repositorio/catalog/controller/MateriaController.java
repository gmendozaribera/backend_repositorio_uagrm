package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.MateriaRequest;
import com.uagrm.repositorio.dto.catalogo.MateriaResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.MateriaService;
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
@RequestMapping("/api/v1/materias")
@RequiredArgsConstructor
public class MateriaController {

    private final MateriaService materiaService;

    @GetMapping
    public ApiResponse<List<MateriaResponse>> listar(
            @RequestParam(required = false) Integer idFacultad,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Materias obtenidas", materiaService.listar(idFacultad, incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MateriaResponse> crear(@Valid @RequestBody MateriaRequest request) {
        return ApiResponse.created("Materia creada", materiaService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MateriaResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody MateriaRequest request) {
        return ApiResponse.ok("Materia actualizada", materiaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<MateriaResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Materia desactivada", materiaService.desactivar(id));
    }
}
