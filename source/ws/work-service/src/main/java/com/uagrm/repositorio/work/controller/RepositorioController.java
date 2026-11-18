package com.uagrm.repositorio.work.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.imp.service.trabajo.RepositorioService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Repositorio de trabajos evaluados (cualquier usuario autenticado). Solo delega en {@link RepositorioService}. */
@RestController
@RequestMapping("/api/v1/repositorio")
@RequiredArgsConstructor
public class RepositorioController {

    private final RepositorioService repositorioService;

    @GetMapping
    public ApiResponse<PaginaResponse<TrabajoRepositorioResumenResponse>> buscar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 50) String hashtag,
            @RequestParam(required = false) @Positive Integer idGrupo,
            @RequestParam(required = false) @Positive Integer idFacultad,
            @RequestParam(required = false) @Positive Integer idCarrera,
            @RequestParam(required = false) @Positive Integer idMateria,
            @RequestParam(required = false) @Positive Integer idSemestre,
            @RequestParam(required = false) @Positive Integer idTipoTrabajo,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        FiltrosRepositorio filtros = new FiltrosRepositorio(q, hashtag, idGrupo, idFacultad, idCarrera, idMateria,
                idSemestre, idTipoTrabajo);
        return ApiResponse.ok("Trabajos obtenidos", repositorioService.buscar(filtros, pagina, tamano));
    }

    @GetMapping("/{id}")
    public ApiResponse<TrabajoPublicoResponse> obtener(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable @Positive Integer id) {
        return ApiResponse.ok("Trabajo obtenido", repositorioService.obtener(jwt, id));
    }
}
