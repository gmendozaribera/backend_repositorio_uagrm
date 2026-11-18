package com.uagrm.repositorio.work.controller;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.EnlaceRequest;
import com.uagrm.repositorio.dto.trabajo.ReglasDocumentosResponse;
import com.uagrm.repositorio.imp.service.documento.DescargaArchivo;
import com.uagrm.repositorio.imp.service.documento.DocumentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Archivos y enlaces de un trabajo. Las descargas pasan siempre por aqui (proxy), nunca por un enlace a MinIO. */
@RestController
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    @PostMapping(path = "/api/v1/trabajos/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentoResponse> subir(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Integer id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam @NotBlank @Size(max = 200) String titulo,
            @RequestParam(required = false) @Size(max = 500) String descripcion,
            @RequestParam @NotNull CategoriaDocumento categoria,
            @RequestParam @NotNull @Positive Integer idFase) {
        return ApiResponse.created("Documento subido",
                documentoService.subir(jwt, id, archivo, titulo, descripcion, categoria, idFase));
    }

    @PostMapping("/api/v1/trabajos/{id}/enlaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentoResponse> agregarEnlace(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable @Positive Integer id,
                                                        @Valid @RequestBody EnlaceRequest request) {
        return ApiResponse.created("Enlace agregado", documentoService.agregarEnlace(jwt, id, request));
    }

    @DeleteMapping("/api/v1/documentos/{id}")
    public ApiResponse<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Integer id) {
        documentoService.eliminar(jwt, id);
        return ApiResponse.ok("Documento eliminado", null);
    }

    /**
     * Descarga en streaming. Cabeceras: attachment (el navegador guarda, no abre), el tipo guardado y nosniff (el
     * navegador no "adivina" otro tipo a partir del contenido).
     */
    @GetMapping("/api/v1/documentos/{id}/descarga")
    public ResponseEntity<InputStreamResource> descargar(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable @Positive Integer id) {
        DescargaArchivo descarga = documentoService.descargar(jwt, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, descarga.contentDisposition())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(descarga.contentType()))
                .contentLength(descarga.tamanoBytes())
                .body(new InputStreamResource(descarga.contenido()));
    }

    @GetMapping("/api/v1/documentos/reglas")
    public ApiResponse<ReglasDocumentosResponse> reglas(@RequestParam @NotNull @Positive Integer idFacultad) {
        return ApiResponse.ok("Reglas de documentos", documentoService.reglas(idFacultad));
    }
}
