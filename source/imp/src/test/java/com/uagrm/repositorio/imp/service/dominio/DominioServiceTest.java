package com.uagrm.repositorio.imp.service.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dao.repository.DominioRepository;
import com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest;
import com.uagrm.repositorio.dto.dominio.CrearDominioRequest;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import com.uagrm.repositorio.dto.mapper.DominioMapperImpl;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.JwtService;
import com.uagrm.repositorio.mockup.DominioMock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class DominioServiceTest {

    private final DominioRepository repository = mock(DominioRepository.class);
    private final ParametrosService parametros = mock(ParametrosService.class);

    private DominioService service;
    private Dominio intentos;
    private Dominio libre;

    @BeforeEach
    void setUp() {
        service = new DominioService(repository, new DominioMapperImpl(), parametros);
        intentos = DominioMock.dominio(1, ConstantesDominio.GRUPO_AUTH, ConstantesDominio.LOGIN_INTENTOS, "10");
        libre = DominioMock.dominio(3, "UI_GROUP", "TEMA", "claro");
        when(repository.findById(1)).thenReturn(Optional.of(intentos));
        when(repository.findById(3)).thenReturn(Optional.of(libre));
        when(repository.save(any())).thenAnswer(inv -> {
            Dominio d = inv.getArgument(0);
            d.setDomainId(9);
            return d;
        });
    }

    @Test
    void listarMarcaLosParametrosDelSistemaYFiltraPorGrupoEnMayusculas() {
        when(repository.findByDomainGroupOrderByDomainNameAsc(ConstantesDominio.GRUPO_AUTH)).thenReturn(List.of(intentos));
        when(repository.findAllByOrderByDomainGroupAscDomainNameAsc()).thenReturn(List.of(intentos, libre));

        assertThat(service.listar(ConstantesDominio.GRUPO_AUTH.toLowerCase())).extracting(DominioResponse::sistema)
                .containsExactly(true);
        assertThat(service.listar(null)).extracting(DominioResponse::sistema).containsExactly(true, false);
    }

    @Test
    void crearGuardaEnMayusculasConAuditoriaEInvalidaLaCacheDelGrupo() {
        DominioResponse creado = service.crear(admin(),
                new CrearDominioRequest("ui_group", "Idioma", " es ", " Idioma por defecto "));

        assertThat(creado.domainGroup()).isEqualTo("UI_GROUP");
        assertThat(creado.domainName()).isEqualTo("IDIOMA");
        assertThat(creado.domainValue()).isEqualTo("es");
        assertThat(creado.description()).isEqualTo("Idioma por defecto");
        assertThat(creado.lastUser()).isEqualTo("admin");
        assertThat(creado.lastTime()).isNotNull();
        assertThat(creado.sistema()).isFalse();
        verify(parametros).invalidar("UI_GROUP");
    }

    @Test
    void crearDuplicadoDevuelve409() {
        when(repository.existsByDomainGroupAndDomainName("UI_GROUP", "TEMA")).thenReturn(true);

        esperar(HttpStatus.CONFLICT, () -> service.crear(admin(), new CrearDominioRequest("UI_GROUP", "tema", "x", "y")));
        verify(repository, never()).save(any());
    }

    @Test
    void actualizarSoloCambiaValorYDescripcionEInvalida() {
        DominioResponse actualizado = service.actualizar(admin(), 1, new ActualizarDominioRequest("3", "Tres intentos"));

        assertThat(actualizado.domainValue()).isEqualTo("3");
        assertThat(actualizado.domainGroup()).isEqualTo(ConstantesDominio.GRUPO_AUTH);
        assertThat(actualizado.domainName()).isEqualTo(ConstantesDominio.LOGIN_INTENTOS);
        assertThat(actualizado.lastUser()).isEqualTo("admin");
        assertThat(actualizado.sistema()).isTrue();
        verify(parametros).invalidar(ConstantesDominio.GRUPO_AUTH);
    }

    @Test
    void valorFueraDeRangoDeUnParametroDelSistemaDevuelve400EnDomainValue() {
        assertThatThrownBy(() -> service.actualizar(admin(), 1, new ActualizarDominioRequest("0", "x")))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrores())
                            .isEqualTo(Map.of("domainValue", "debe ser un numero entero entre 1 y 1000"));
                });
        verify(parametros, never()).invalidar(any());

        // Un parametro libre acepta cualquier texto
        service.actualizar(admin(), 3, new ActualizarDominioRequest("oscuro", "Tema"));
    }

    @Test
    void eliminarEsFisicoYLosDelSistemaNoSePuedenEliminar() {
        esperar(HttpStatus.CONFLICT, () -> service.eliminar(1));
        verify(repository, never()).delete(any());

        service.eliminar(3);
        verify(repository).delete(libre);
        verify(parametros).invalidar("UI_GROUP");
    }

    @Test
    void inexistenteDevuelve404() {
        esperar(HttpStatus.NOT_FOUND, () -> service.obtener(99));
    }

    private static Jwt admin() {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("1")
                .claim(JwtService.CLAIM_USUARIO, "admin").build();
    }

    private static void esperar(HttpStatus status, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(NegocioException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }
}
