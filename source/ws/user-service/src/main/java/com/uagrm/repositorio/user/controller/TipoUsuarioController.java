package com.uagrm.repositorio.user.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.usuario.TipoUsuarioResponse;
import com.uagrm.repositorio.imp.service.usuario.UsuarioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tipos-usuario")
@RequiredArgsConstructor
public class TipoUsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ApiResponse<List<TipoUsuarioResponse>> listar() {
        return ApiResponse.ok("Roles obtenidos", usuarioService.listarTipos());
    }
}
