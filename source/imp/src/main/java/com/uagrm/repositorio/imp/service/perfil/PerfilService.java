package com.uagrm.repositorio.imp.service.perfil;

import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.mapper.NotificacionMapper;
import com.uagrm.repositorio.dto.perfil.Preferencias;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preferencias del usuario autenticado (/api/v1/perfil/preferencias). Siempre opera sobre el usuario del
 * token: no hay forma de leer o cambiar las preferencias de otra persona.
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final NotificacionMapper mapper;

    @Transactional(readOnly = true)
    public Preferencias obtenerPreferencias(Jwt yo) {
        return mapper.toPreferencias(buscar(yo));
    }

    @Transactional
    public Preferencias actualizarPreferencias(Jwt yo, Preferencias preferencias) {
        Usuario usuario = buscar(yo);
        usuario.setNotificarCorreo(preferencias.notificarCorreo());
        return mapper.toPreferencias(usuario);
    }

    /** No hay borrado fisico, asi que solo pasa con un token de una BD distinta: se trata como sesion invalida. */
    private Usuario buscar(Jwt yo) {
        return usuarioRepository.findById(UsuarioAutenticado.id(yo))
                .orElseThrow(() -> NegocioException.noAutorizado("La cuenta de la sesion ya no existe."));
    }
}
