package com.uagrm.repositorio.imp.service.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Notificacion;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.NotificacionRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.NotificacionMapperImpl;
import com.uagrm.repositorio.dto.notificacion.NotificacionResponse;
import com.uagrm.repositorio.dto.notificacion.NotificacionesPendientesResponse;
import com.uagrm.repositorio.imp.config.NotificacionCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.correo.CorreoService;
import com.uagrm.repositorio.mockup.NotificacionMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificacionServiceTest {

    private static final int ID_USUARIO = 7;
    private static final String CLAVE = "notif:pendientes:7";
    private static final Duration TTL = Duration.ofDays(1);

    private final NotificacionRepository repository = mock(NotificacionRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final CorreoService correoService = mock(CorreoService.class);
    /** Tareas de correo encoladas: el test decide cuando "corre" el hilo de fondo. */
    private final List<Runnable> enSegundoPlano = new ArrayList<>();

    private NotificacionService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new NotificacionService(repository, usuarioRepository, redis, parametros, correoService,
                new NotificacionMapperImpl(), new NotificacionCacheProperties(TTL), enSegundoPlano::add);
        usuario = UsuarioMock.usuario(ID_USUARIO, "est.ana", TipoUsuario.ESTUDIANTE);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(redis.opsForValue()).thenReturn(valores);
        when(parametros.entero(Parametro.NOTIF_POLLING_SEGUNDOS)).thenReturn(30);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------- contador de pendientes ----------

    @Test
    void conElContadorEnCacheNoSeTocaLaBd() {
        when(valores.get(CLAVE)).thenReturn("3");

        NotificacionesPendientesResponse respuesta = service.pendientes(jwt(ID_USUARIO));

        assertThat(respuesta.cantidad()).isEqualTo(3);
        assertThat(respuesta.pollingSegundos()).isEqualTo(30);
        verify(repository, never()).countByIdUsuarioAndLeidaFalse(any());
    }

    @Test
    void sinContadorSeCuentaEnLaBdYSeGuardaConTtl() {
        when(valores.get(CLAVE)).thenReturn(null);
        when(repository.countByIdUsuarioAndLeidaFalse(ID_USUARIO)).thenReturn(2L);

        assertThat(service.pendientes(jwt(ID_USUARIO)).cantidad()).isEqualTo(2);
        verify(valores).set(CLAVE, "2", TTL);
    }

    @Test
    void contadorCorruptoSeRecalcula() {
        when(valores.get(CLAVE)).thenReturn("basura");
        when(repository.countByIdUsuarioAndLeidaFalse(ID_USUARIO)).thenReturn(1L);

        assertThat(service.contarNoLeidas(ID_USUARIO)).isEqualTo(1);
        verify(valores).set(CLAVE, "1", TTL);
    }

    @Test
    void redisCaidoCuentaEnPostgresSin503() {
        when(valores.get(CLAVE)).thenThrow(new RedisConnectionFailureException("caido"));
        when(repository.countByIdUsuarioAndLeidaFalse(ID_USUARIO)).thenReturn(4L);

        assertThat(service.pendientes(jwt(ID_USUARIO)).cantidad()).isEqualTo(4);
        verify(valores, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisCaidoAlGuardarIgualDevuelveElConteo() {
        when(valores.get(CLAVE)).thenReturn(null);
        when(repository.countByIdUsuarioAndLeidaFalse(ID_USUARIO)).thenReturn(5L);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("caido"))
                .when(valores).set(CLAVE, "5", TTL);

        assertThat(service.contarNoLeidas(ID_USUARIO)).isEqualTo(5);
    }

    // ---------- notificar ----------

    @Test
    void notificarGuardaYSoloDespuesDelCommitIncrementaElContador() {
        TransactionSynchronizationManager.initSynchronization();

        service.notificar(ID_USUARIO, TipoNotificacion.BIENVENIDA, "Titulo", "Mensaje", "/perfil/seguridad");

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).save(captor.capture());
        Notificacion guardada = captor.getValue();
        assertThat(guardada.getIdUsuario()).isEqualTo(ID_USUARIO);
        assertThat(guardada.getTipo()).isEqualTo(TipoNotificacion.BIENVENIDA);
        assertThat(guardada.getLeida()).isFalse();
        assertThat(guardada.getFechaCreacion()).isNotNull();
        assertThat(guardada.getEnlace()).isEqualTo("/perfil/seguridad");
        // Antes del commit Redis no se toca (si la transaccion se revierte, el contador no cambia)
        verify(redis, never()).execute(eq(NotificacionService.INCREMENTAR_SI_EXISTE), any(), any());

        confirmarTransaccion();
        verify(redis).execute(eq(NotificacionService.INCREMENTAR_SI_EXISTE), eq(List.of(CLAVE)), eq("86400"));
    }

    @Test
    void sinPreferenciaDeCorreoNoSeEnviaCorreo() {
        service.notificar(ID_USUARIO, TipoNotificacion.BIENVENIDA, "Titulo", "Mensaje", null);

        assertThat(enSegundoPlano).isEmpty();
        verify(correoService, never()).enviar(any(), any(), any());
    }

    @Test
    void conPreferenciaElCorreoSaleDespuesDelCommitYEnSegundoPlano() {
        usuario.setNotificarCorreo(true);
        TransactionSynchronizationManager.initSynchronization();

        service.notificar(ID_USUARIO, TipoNotificacion.PASSWORD_RESTABLECIDA, "Tu contrasena fue restablecida",
                "Mensaje", null);
        assertThat(enSegundoPlano).isEmpty();

        confirmarTransaccion();
        assertThat(enSegundoPlano).hasSize(1);
        verify(correoService, never()).enviar(any(), any(), any());

        enSegundoPlano.getFirst().run();
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviar(eq("est.ana@uagrm.edu.bo"),
                eq("Repositorio Digital UAGRM - Tu contrasena fue restablecida"), texto.capture());
        assertThat(texto.getValue()).startsWith("Mensaje").contains("no lo responda");
    }

    @Test
    void siLaTransaccionSeRevierteNoHayContadorNiCorreo() {
        usuario.setNotificarCorreo(true);
        TransactionSynchronizationManager.initSynchronization();

        service.notificar(ID_USUARIO, TipoNotificacion.BIENVENIDA, "Titulo", "Mensaje", null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(redis, never()).execute(eq(NotificacionService.INCREMENTAR_SI_EXISTE), any(), any());
        assertThat(enSegundoPlano).isEmpty();
    }

    @Test
    void unFalloDelCorreoNuncaRompeLaOperacion() {
        usuario.setNotificarCorreo(true);
        when(correoService.enviar(any(), any(), any())).thenThrow(new NegocioException(HttpStatus.BAD_REQUEST,
                "Configuracion de correo invalida", "x"));

        service.notificar(ID_USUARIO, TipoNotificacion.BIENVENIDA, "Titulo", "Mensaje", null);

        assertThatCode(() -> enSegundoPlano.getFirst().run()).doesNotThrowAnyException();
    }

    @Test
    void redisCaidoAlIncrementarNoRompeLaOperacionEIntentaBorrarElContador() {
        when(redis.execute(eq(NotificacionService.INCREMENTAR_SI_EXISTE), any(), any()))
                .thenThrow(new RedisConnectionFailureException("caido"));
        when(redis.delete(CLAVE)).thenThrow(new RedisConnectionFailureException("caido"));

        assertThatCode(() -> service.notificar(ID_USUARIO, TipoNotificacion.BIENVENIDA, "T", "M", null))
                .doesNotThrowAnyException();
        verify(redis).delete(CLAVE);
    }

    @Test
    void notificarAUnUsuarioInexistenteDevuelve404() {
        assertThatThrownBy(() -> service.notificar(99, TipoNotificacion.BIENVENIDA, "T", "M", null))
                .isInstanceOfSatisfying(NegocioException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repository, never()).save(any());
    }

    // ---------- listar / marcar ----------

    @Test
    void listarUsaElUsuarioDelTokenYOrdenaPorFechaDescendente() {
        Page<Notificacion> pagina = new PageImpl<>(List.of(NotificacionMock.noLeida(1, ID_USUARIO)),
                PageRequest.of(0, 20), 1);
        when(repository.findByIdUsuarioAndLeidaFalse(eq(ID_USUARIO), any())).thenReturn(pagina);

        PaginaResponse<NotificacionResponse> respuesta = service.listar(jwt(ID_USUARIO), true, 0, 20);

        assertThat(respuesta.contenido()).hasSize(1);
        assertThat(respuesta.contenido().getFirst().tipo()).isEqualTo("BIENVENIDA");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByIdUsuarioAndLeidaFalse(eq(ID_USUARIO), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("fechaCreacion").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        verify(repository, never()).findByIdUsuario(any(), any());
    }

    @Test
    void marcarLeidaEsIdempotenteYBorraElContadorDespuesDelCommit() {
        Notificacion notificacion = NotificacionMock.noLeida(5, ID_USUARIO);
        when(repository.findByIdNotificacionAndIdUsuario(5, ID_USUARIO)).thenReturn(Optional.of(notificacion));
        TransactionSynchronizationManager.initSynchronization();

        NotificacionResponse respuesta = service.marcarLeida(jwt(ID_USUARIO), 5);
        assertThat(respuesta.leida()).isTrue();
        Instant leida = notificacion.getFechaLectura();
        assertThat(leida).isNotNull();
        verify(redis, never()).delete(anyString());
        confirmarTransaccion();
        verify(redis).delete(CLAVE);

        TransactionSynchronizationManager.clearSynchronization();
        service.marcarLeida(jwt(ID_USUARIO), 5);
        assertThat(notificacion.getFechaLectura()).isEqualTo(leida);
    }

    @Test
    void notificacionAjenaOInexistenteDevuelve404() {
        when(repository.findByIdNotificacionAndIdUsuario(5, ID_USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarLeida(jwt(ID_USUARIO), 5))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("No existe la notificacion 5.");
                });
        verify(redis, never()).delete(anyString());
    }

    @Test
    void marcarTodasLeidasActualizaSoloLasDelUsuarioYBorraElContador() {
        service.marcarTodasLeidas(jwt(ID_USUARIO));

        verify(repository).marcarTodasLeidas(eq(ID_USUARIO), any(Instant.class));
        verify(redis).delete(CLAVE);
    }

    // ---------- purga ----------

    @Test
    void purgaBorraLasLeidasAnterioresALaRetencion() {
        when(parametros.entero(Parametro.NOTIF_RETENCION_DIAS)).thenReturn(90);
        when(repository.borrarLeidasAnterioresA(any())).thenReturn(4);

        Instant antes = Instant.now().minus(Duration.ofDays(90));
        assertThat(service.purgarLeidasAntiguas()).isEqualTo(4);
        Instant despues = Instant.now().minus(Duration.ofDays(90));

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(repository).borrarLeidasAnterioresA(limite.capture());
        assertThat(limite.getValue()).isBetween(antes, despues);
    }

    // ---------- helpers ----------

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }
}
