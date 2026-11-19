package com.uagrm.repositorio.imp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.ActivarDosFactoresResponse;
import com.uagrm.repositorio.dto.auth.CodigoTotpRequest;
import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.CifradorTotp;
import com.uagrm.repositorio.imp.security.CifradorTotpTest;
import com.uagrm.repositorio.imp.security.TotpService;
import com.uagrm.repositorio.mockup.UsuarioMock;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 2FA con el secreto TOTP cifrado (S5): activar guarda {@code v1:...} (nunca el secreto en claro), confirmar,
 * verificar (lo que usa el paso 2 del login) y desactivar funcionan descifrando en memoria; con otra llave falla.
 */
class DosFactoresServiceTest {

    private static final SeguridadProperties PROPS = new SeguridadProperties(
            new SeguridadProperties.Jwt("x", "test", Duration.ofDays(7)),
            new SeguridadProperties.DosFactores("Repositorio UAGRM", Duration.ofMinutes(5), 5),
            List.of("http://localhost:4200"));

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final TotpService totpService = new TotpService(PROPS, new CifradorTotp(CifradorTotpTest.LLAVE));
    private final DosFactoresService service = new DosFactoresService(usuarioRepository, authService, totpService);

    private Usuario usuario;
    private Jwt token;

    @BeforeEach
    void setUp() {
        usuario = UsuarioMock.estudiante();
        when(authService.buscarActivo(anyInt())).thenReturn(usuario);
        token = Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(usuario.getIdUsuario())).build();
    }

    @Test
    void cicloCompletoConElSecretoCifradoEnLaBd() throws Exception {
        ActivarDosFactoresResponse qr = service.iniciarActivacion(token);
        String enClaro = qr.secreto();

        // En la BD queda cifrado: ni el secreto ni algo derivable a simple vista
        assertThat(usuario.getTotpSecret()).startsWith("v1:").doesNotContain(enClaro);

        service.confirmarActivacion(token, new CodigoTotpRequest(codigo(enClaro)));
        assertThat(usuario.getTwoFactorEnabled()).isTrue();

        // Paso 2 del login (AuthService.verificarDosFactores usa exactamente esto)
        assertThat(totpService.esValido(usuario.getTotpSecret(), codigo(enClaro))).isTrue();
        assertThat(totpService.esValido(usuario.getTotpSecret(), otroCodigo(codigo(enClaro)))).isFalse();

        service.desactivar(token, new CodigoTotpRequest(codigo(enClaro)));
        assertThat(usuario.getTwoFactorEnabled()).isFalse();
        assertThat(usuario.getTotpSecret()).isNull();
    }

    @Test
    void codigoIncorrectoNoConfirma() throws Exception {
        String malo = otroCodigo(codigo(service.iniciarActivacion(token).secreto()));

        assertThatThrownBy(() -> service.confirmarActivacion(token, new CodigoTotpRequest(malo)))
                .isInstanceOf(NegocioException.class);
        assertThat(usuario.getTwoFactorEnabled()).isFalse();
    }

    @Test
    void conOtraLlaveLaVerificacionFallaCerrada() throws Exception {
        ActivarDosFactoresResponse qr = service.iniciarActivacion(token);
        TotpService otraLlave = new TotpService(PROPS,
                new CifradorTotp("b3RyYS1sbGF2ZS1kZS1wcnVlYmEtdG90cC0zMmJ5dGU="));

        // Nunca "acepta" el codigo: es un error del servidor (500 generico), no un 2FA desactivado
        String guardado = usuario.getTotpSecret();
        String codigo = codigo(qr.secreto());
        assertThatThrownBy(() -> otraLlave.esValido(guardado, codigo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(qr.secreto());
    }

    @Test
    void sinSecretoNoHayCodigoValido() {
        assertThat(totpService.esValido(null, "123456")).isFalse();
    }

    /** Un codigo de 6 digitos distinto del valido (cambia el ultimo digito). */
    private static String otroCodigo(String valido) {
        char ultimo = valido.charAt(5);
        return valido.substring(0, 5) + (ultimo == '9' ? '0' : (char) (ultimo + 1));
    }

    private static String codigo(String secreto) throws Exception {
        return new DefaultCodeGenerator().generate(secreto, Math.floorDiv(Instant.now().getEpochSecond(), 30));
    }
}
