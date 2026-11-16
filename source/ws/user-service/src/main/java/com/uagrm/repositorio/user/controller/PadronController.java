package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.padron.ActualizarHabilitadoRequest;
import com.uagrm.repositorio.dto.padron.EstudianteHabilitadoResponse;
import com.uagrm.repositorio.dto.padron.ResultadoImportacion;
import com.uagrm.repositorio.imp.service.padron.PadronService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/padron")
@RequiredArgsConstructor
public class PadronController {

    private final PadronService padronService;

    @GetMapping
    public ApiResponse<PaginaResponse<EstudianteHabilitadoResponse>> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Positive Integer idCarrera,
            @RequestParam(required = false) Boolean registrado,
            @RequestParam(required = false) Boolean habilitado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ApiResponse.ok("Padron obtenido",
                padronService.listar(q, idCarrera, registrado, habilitado, pagina, tamano));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResultadoImportacion> importar(@RequestPart("archivo") MultipartFile archivo) {
        ResultadoImportacion resultado = padronService.importar(archivo);
        return ApiResponse.ok(resultado.conErrores() == 0
                ? "Padron importado"
                : "Padron importado con " + resultado.conErrores() + " filas con errores", resultado);
    }

    @PatchMapping("/{id}")
    public ApiResponse<EstudianteHabilitadoResponse> actualizarHabilitado(
            @PathVariable Integer id, @Valid @RequestBody ActualizarHabilitadoRequest request) {
        return ApiResponse.ok(Boolean.TRUE.equals(request.habilitado())
                ? "Estudiante habilitado" : "Estudiante inhabilitado",
                padronService.actualizarHabilitado(id, request));
    }
}
