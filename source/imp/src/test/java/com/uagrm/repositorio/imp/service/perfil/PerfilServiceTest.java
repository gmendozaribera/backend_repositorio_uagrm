package com.uagrm.repositorio.imp.service.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.mapper.NotificacionMapperImpl;
import com.uagrm.repositorio.dto.perfil.Preferencias;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class PerfilServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private PerfilService service;
    private Usuario docente;

    @BeforeEach
    void setUp() {
        service = new PerfilService(usuarioRepository, new NotificacionMapperImpl());
        docente = UsuarioMock.usuario(5, "doc.maria", TipoUsuario.DOCENTE);
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(docente));
    }

    @Test
    void porDefectoNoSeNotificaPorCorreo() {
        assertThat(service.obtenerPreferencias(jwt(5)).notificarCorreo()).isFalse();
    }

    @Test
    void actualizarCambiaSoloLaCuentaDelToken() {
        Preferencias guardadas = service.actualizarPreferencias(jwt(5), new Preferencias(true));

        assertThat(guardadas.notificarCorreo()).isTrue();
        assertThat(docente.getNotificarCorreo()).isTrue();
        assertThat(service.obtenerPreferencias(jwt(5)).notificarCorreo()).isTrue();
    }

    @Test
    void cuentaInexistenteDevuelve401() {
        assertThatThrownBy(() -> service.obtenerPreferencias(jwt(99)))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }
}
