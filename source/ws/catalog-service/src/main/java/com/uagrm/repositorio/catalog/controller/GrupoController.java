package com.uagrm.repositorio.catalog.controller;

import com.uagrm.repositorio.dto.catalogo.ActualizarDocenteGrupoRequest;
import com.uagrm.repositorio.dto.catalogo.AsignarDocenteRequest;
import com.uagrm.repositorio.dto.catalogo.DocenteGrupoResponse;
import com.uagrm.repositorio.dto.catalogo.GrupoRequest;
import com.uagrm.repositorio.dto.catalogo.GrupoResponse;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.imp.service.catalogo.GrupoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;

    @GetMapping
    public ApiResponse<List<GrupoResponse>> listar(
            @RequestParam(required = false) Integer idFacultad,
            @RequestParam(required = false) Integer idMateria,
            @RequestParam(required = false) Integer idSemestre,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ApiResponse.ok("Grupos obtenidos", grupoService.listar(idFacultad, idMateria, idSemestre, incluirInactivos));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GrupoResponse> crear(@Valid @RequestBody GrupoRequest request) {
        return ApiResponse.created("Grupo creado", grupoService.crear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GrupoResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody GrupoRequest request) {
        return ApiResponse.ok("Grupo actualizado", grupoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<GrupoResponse> desactivar(@PathVariable Integer id) {
        return ApiResponse.ok("Grupo desactivado", grupoService.desactivar(id));
    }

    @GetMapping("/{id}/docentes")
    public ApiResponse<List<DocenteGrupoResponse>> listarDocentes(@PathVariable Integer id) {
        return ApiResponse.ok("Docentes del grupo obtenidos", grupoService.listarDocentes(id));
    }

    @PostMapping("/{id}/docentes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocenteGrupoResponse> asignarDocente(@PathVariable Integer id,
                                                            @Valid @RequestBody AsignarDocenteRequest request) {
        return ApiResponse.created("Docente asignado al grupo", grupoService.asignarDocente(id, request));
    }

    @PatchMapping("/{id}/docentes/{idUsuario}")
    public ApiResponse<DocenteGrupoResponse> actualizarDocente(@PathVariable Integer id, @PathVariable Integer idUsuario,
                                                               @Valid @RequestBody ActualizarDocenteGrupoRequest request) {
        return ApiResponse.ok("Asignacion del docente actualizada", grupoService.actualizarDocente(id, idUsuario, request));
    }

    @DeleteMapping("/{id}/docentes/{idUsuario}")
    public ApiResponse<Void> quitarDocente(@PathVariable Integer id, @PathVariable Integer idUsuario) {
        grupoService.quitarDocente(id, idUsuario);
        return ApiResponse.ok("Docente quitado del grupo", null);
    }
}
