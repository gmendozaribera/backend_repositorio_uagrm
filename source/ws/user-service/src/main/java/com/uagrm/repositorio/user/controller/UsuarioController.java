package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.usuario.ActualizarUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.CrearUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.ReglasUsuario;
import com.uagrm.repositorio.dto.usuario.RestablecerPasswordRequest;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import com.uagrm.repositorio.imp.service.usuario.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ApiResponse<PaginaResponse<UsuarioResponse>> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Pattern(regexp = ReglasUsuario.PATRON, message = ReglasUsuario.MENSAJE)
            String rol,
            @RequestParam(required = false) @Positive Integer idCarrera,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ApiResponse.ok("Usuarios obtenidos",
                usuarioService.listar(q, rol, idCarrera, incluirInactivos, pagina, tamano));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ApiResponse.created("Usuario creado", usuarioService.crear(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UsuarioResponse> obtener(@PathVariable Integer id) {
        return ApiResponse.ok("Usuario obtenido", usuarioService.obtener(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<UsuarioResponse> actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                   @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ApiResponse.ok("Usuario actualizado", usuarioService.actualizar(jwt, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<UsuarioResponse> desactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ApiResponse.ok("Usuario desactivado", usuarioService.desactivar(jwt, id));
    }

    @PostMapping("/{id}/password/reset")
    public ApiResponse<Void> restablecerPassword(@PathVariable Integer id,
                                                 @Valid @RequestBody RestablecerPasswordRequest request) {
        usuarioService.restablecerPassword(id, request);
        return ApiResponse.ok("Contrasena restablecida. El usuario debera cambiarla al iniciar sesion.", null);
    }
}
