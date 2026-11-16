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
import com.uagrm.repositorio.imp.dominio.CifradorDominio;
import com.uagrm.repositorio.imp.dominio.CifradorDominioTest;
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
    private final CifradorDominio cifrador = new CifradorDominio(CifradorDominioTest.LLAVE);

    private DominioService service;
    private Dominio intentos;
    private Dominio libre;
    private Dominio password;

    @BeforeEach
    void setUp() {
        service = new DominioService(repository, new DominioMapperImpl(), parametros, cifrador);
        password = DominioMock.dominio(5, ConstantesDominio.GRUPO_SMTP, ConstantesDominio.PASSWORD, "");
        when(repository.findById(5)).thenReturn(Optional.of(password));
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
    void unSecretoSeGuardaCifradoYNuncaSeDevuelve() {
        DominioResponse respuesta = service.actualizar(admin(), 5,
                new ActualizarDominioRequest(" clave con espacios ", "Contrasena SMTP"));

        assertThat(password.getDomainValue()).startsWith(CifradorDominio.PREFIJO);
        // No se recorta: los espacios pueden ser parte de la contrasena
        assertThat(cifrador.descifrar(password.getDomainValue())).isEqualTo(" clave con espacios ");
        assertThat(respuesta.domainValue()).isEmpty();
        assertThat(respuesta.secreto()).isTrue();
        assertThat(respuesta.configurado()).isTrue();
        assertThat(service.obtener(5).domainValue()).isEmpty();
        verify(parametros).invalidar(ConstantesDominio.GRUPO_SMTP);
    }

    @Test
    void putSoloConDescripcionConservaElSecretoCifradoYPutVacioLoBorra() {
        String cifrado = cifrador.cifrar("clave-actual");
        password.setDomainValue(cifrado);

        DominioResponse soloDescripcion = service.actualizar(admin(), 5,
                new ActualizarDominioRequest(null, " Nueva descripcion "));

        assertThat(password.getDomainValue()).isEqualTo(cifrado);
        assertThat(password.getDescription()).isEqualTo("Nueva descripcion");
        assertThat(soloDescripcion.configurado()).isTrue();
        assertThat(soloDescripcion.domainValue()).isEmpty();

        DominioResponse borrado = service.actualizar(admin(), 5, new ActualizarDominioRequest("", "Sin contrasena"));
        assertThat(password.getDomainValue()).isEmpty();
        assertThat(borrado.configurado()).isFalse();
    }

    @Test
    void noSecretoConValorNullConservaElValorActual() {
        DominioResponse respuesta = service.actualizar(admin(), 1, new ActualizarDominioRequest(null, "Solo descripcion"));

        assertThat(intentos.getDomainValue()).isEqualTo("10");
        assertThat(respuesta.domainValue()).isEqualTo("10");
        assertThat(respuesta.description()).isEqualTo("Solo descripcion");
        verify(parametros).invalidar(ConstantesDominio.GRUPO_AUTH);
    }

    @Test
    void unSecretoVacioQuedaSinConfigurar() {
        DominioResponse respuesta = service.actualizar(admin(), 5, new ActualizarDominioRequest("", "Sin contrasena"));

        assertThat(password.getDomainValue()).isEmpty();
        assertThat(respuesta.configurado()).isFalse();
        assertThat(respuesta.secreto()).isTrue();
    }

    @Test
    void losNoSecretosSiempreEstanConfiguradosYNoAdmitenVacioSalvoQueElParametroLoPermita() {
        DominioResponse intentosResp = service.obtener(1);
        assertThat(intentosResp.secreto()).isFalse();
        assertThat(intentosResp.configurado()).isTrue();
        assertThat(intentosResp.domainValue()).isEqualTo("10");

        assertThatThrownBy(() -> service.actualizar(admin(), 1, new ActualizarDominioRequest(" ", "x")))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getErrores()).isEqualTo(Map.of("domainValue", "no puede estar vacio")));
        assertThatThrownBy(() -> service.actualizar(admin(), 3, new ActualizarDominioRequest("", "x")))
                .isInstanceOf(NegocioException.class);
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
