package com.uagrm.repositorio.imp.service.notificacion;

import com.uagrm.repositorio.dao.entity.Notificacion;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.NotificacionRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.NotificacionMapper;
import com.uagrm.repositorio.dto.notificacion.NotificacionResponse;
import com.uagrm.repositorio.dto.notificacion.NotificacionesPendientesResponse;
import com.uagrm.repositorio.imp.config.NotificacionCacheProperties;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.ClavesRedis;
import com.uagrm.repositorio.imp.security.UsuarioAutenticado;
import com.uagrm.repositorio.imp.service.correo.CorreoService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notificaciones in-app (campana del frontend) y su copia opcional por correo. Vive en imp para que cualquier
 * servicio pueda notificar con {@link #notificar} (user-service y auth-service en el Sprint 2; work-service en el 3).
 *
 * <p><b>Contador de no leidas</b> en Redis, {@code notif:pendientes:{idUsuario}} (TTL 1 dia, renovado al escribir):</p>
 * <ul>
 *   <li>Leer: GET; si no existe → COUNT en PostgreSQL y SET con TTL. Asi el polling de la campana no toca la BD.</li>
 *   <li>Crear: INCR solo si la key existe (si no, se recalculara al leer), DESPUES del commit: si la transaccion
 *       se revierte, el contador no cambia.</li>
 *   <li>Marcar leida(s): DEL despues del commit (la siguiente lectura recalcula desde la BD).</li>
 *   <li>Redis caido: log warn y COUNT en PostgreSQL. No es dato de seguridad, asi que aqui NO hay 503 (el 503
 *       por sesion lo sigue dando el filtro de seguridad de cada servicio).</li>
 *   <li>Carrera conocida y aceptada: si una notificacion se crea justo entre el COUNT y el SET de otra lectura,
 *       el contador puede quedar una unidad abajo hasta que el usuario marque algo como leido o expire el TTL.</li>
 * </ul>
 *
 * <p><b>Correo:</b> solo si el usuario activo {@code notificar_correo}. Se envia despues del commit y en segundo
 * plano: un servidor de correo lento (hasta 15 s) o caido nunca demora ni revierte la operacion que notifico.
 * CorreoService ademas respeta SMTP_GROUP.ACTIVO. Los logs nunca incluyen la direccion de correo.</p>
 */
@Slf4j
@Service
public class NotificacionService {

    /**
     * INCR + EXPIRE solo si la key existe (si no existe, sumar 1 a "nada" daria un contador falso de 1).
     * KEYS[1] = contador; ARGV[1] = TTL en segundos. Devuelve el contador o -1 si no existia.
     */
    static final RedisScript<Long> INCREMENTAR_SI_EXISTE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              local cantidad = redis.call('INCR', KEYS[1])
              redis.call('EXPIRE', KEYS[1], ARGV[1])
              return cantidad
            end
            return -1""", Long.class);

    static final String PREFIJO_ASUNTO = "Repositorio Digital UAGRM - ";
    static final String PIE_CORREO = """


            Ingrese al Repositorio Digital para ver el detalle.
            Este es un mensaje automatico; no lo responda. Puede dejar de recibir estos correos desde
            Mi perfil > Preferencias.""";

    /** Mas recientes primero; el id desempata las creadas en el mismo instante. */
    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaCreacion", "idNotificacion");

    private final NotificacionRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final StringRedisTemplate redis;
    private final ParametrosService parametros;
    private final CorreoService correoService;
    private final NotificacionMapper mapper;
    private final NotificacionCacheProperties props;
    private final Executor correoExecutor;

    public NotificacionService(NotificacionRepository repository, UsuarioRepository usuarioRepository,
                               StringRedisTemplate redis, ParametrosService parametros, CorreoService correoService,
                               NotificacionMapper mapper, NotificacionCacheProperties props,
                               @Qualifier("applicationTaskExecutor") Executor correoExecutor) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.redis = redis;
        this.parametros = parametros;
        this.correoService = correoService;
        this.mapper = mapper;
        this.props = props;
        this.correoExecutor = correoExecutor;
    }

    // ---------- Crear (uso interno de otros services) ----------

    /**
     * Guarda una notificacion para el usuario (en la transaccion del que llama: si esta se revierte, la
     * notificacion tampoco existe). Despues del commit suma 1 al contador y, si el usuario lo pidio, envia el correo.
     *
     * @param enlace ruta interna del frontend o null
     * @throws NegocioException 404 si el usuario no existe
     */
    @Transactional
    public void notificar(Integer idUsuario, TipoNotificacion tipo, String titulo, String mensaje, String enlace) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el usuario " + idUsuario + "."));
        Notificacion notificacion = new Notificacion();
        notificacion.setIdUsuario(idUsuario);
        notificacion.setTipo(tipo);
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacion.setEnlace(enlace);
        notificacion.setLeida(Boolean.FALSE);
        notificacion.setFechaCreacion(Instant.now());
        repository.save(notificacion);

        // Se lee ahora (dentro de la transaccion): despues del commit la sesion JPA ya no sirve para cargar datos
        String correo = Boolean.TRUE.equals(usuario.getNotificarCorreo()) ? usuario.getPersona().getCorreo() : null;
        despuesDelCommit(() -> {
            incrementar(idUsuario);
            if (correo != null) {
                enviarCorreo(idUsuario, tipo, correo, titulo, mensaje);
            }
        });
    }

    // ---------- Endpoints (siempre sobre el usuario del token) ----------

    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionResponse> listar(Jwt yo, boolean soloNoLeidas, int pagina, int tamano) {
        Integer idUsuario = UsuarioAutenticado.id(yo);
        PageRequest pageable = PageRequest.of(pagina, tamano, ORDEN);
        Page<Notificacion> page = soloNoLeidas
                ? repository.findByIdUsuarioAndLeidaFalse(idUsuario, pageable)
                : repository.findByIdUsuario(idUsuario, pageable);
        return PaginaResponse.de(page, mapper::toResponse);
    }

    /** Sin @Transactional a proposito: con el contador en cache no se abre ninguna conexion a la BD. */
    public NotificacionesPendientesResponse pendientes(Jwt yo) {
        return new NotificacionesPendientesResponse(contarNoLeidas(UsuarioAutenticado.id(yo)),
                parametros.entero(Parametro.NOTIF_POLLING_SEGUNDOS));
    }

    /**
     * Idempotente (marcar dos veces no cambia la fecha de lectura). 404 si no existe o es de otro usuario: el
     * mismo mensaje en ambos casos, asi nadie puede averiguar que ids existen probando numeros.
     */
    @Transactional
    public NotificacionResponse marcarLeida(Jwt yo, Integer idNotificacion) {
        Integer idUsuario = UsuarioAutenticado.id(yo);
        Notificacion notificacion = repository.findByIdNotificacionAndIdUsuario(idNotificacion, idUsuario)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la notificacion " + idNotificacion + "."));
        if (!Boolean.TRUE.equals(notificacion.getLeida())) {
            notificacion.setLeida(Boolean.TRUE);
            notificacion.setFechaLectura(Instant.now());
        }
        despuesDelCommit(() -> invalidar(idUsuario));
        return mapper.toResponse(notificacion);
    }

    @Transactional
    public void marcarTodasLeidas(Jwt yo) {
        Integer idUsuario = UsuarioAutenticado.id(yo);
        repository.marcarTodasLeidas(idUsuario, Instant.now());
        despuesDelCommit(() -> invalidar(idUsuario));
    }

    // ---------- Purga (job diario de user-service) ----------

    /**
     * Borra las notificaciones LEIDAS creadas hace mas de NOTIF_GROUP.RETENCION_DIAS dias. Idempotente: correrla
     * dos veces (o en dos replicas a la vez) solo borra lo que aun quede. No toca el contador: solo cuenta no leidas.
     *
     * @return cantidad de notificaciones borradas
     */
    @Transactional
    public int purgarLeidasAntiguas() {
        int dias = parametros.entero(Parametro.NOTIF_RETENCION_DIAS);
        Instant limite = Instant.now().minus(Duration.ofDays(dias));
        int borradas = repository.borrarLeidasAnterioresA(limite);
        log.info("Purga de notificaciones: {} leidas anteriores a {} ({} dias) borradas", borradas, limite, dias);
        return borradas;
    }

    // ---------- Contador en Redis ----------

    /** Cantidad de no leidas: Redis si esta; si no, COUNT en la BD (y se cachea). Redis caido → solo la BD. */
    public long contarNoLeidas(Integer idUsuario) {
        String clave = ClavesRedis.notificacionesPendientes(idUsuario);
        String guardado;
        try {
            guardado = redis.opsForValue().get(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al leer {}; se cuenta en PostgreSQL: {}", clave, ex.getMessage());
            return repository.countByIdUsuarioAndLeidaFalse(idUsuario);
        }
        if (guardado != null) {
            try {
                long cantidad = Long.parseLong(guardado);
                if (cantidad >= 0) {
                    return cantidad;
                }
            } catch (NumberFormatException ex) {
                // valor corrupto: se recalcula y se pisa abajo
            }
            log.warn("Valor invalido en {}; se recalcula desde PostgreSQL", clave);
        }
        long cantidad = repository.countByIdUsuarioAndLeidaFalse(idUsuario);
        try {
            redis.opsForValue().set(clave, Long.toString(cantidad), props.ttl());
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al guardar {}; el contador no queda cacheado: {}", clave, ex.getMessage());
        }
        return cantidad;
    }

    private void incrementar(Integer idUsuario) {
        String clave = ClavesRedis.notificacionesPendientes(idUsuario);
        try {
            redis.execute(INCREMENTAR_SI_EXISTE, List.of(clave), Long.toString(props.ttl().toSeconds()));
        } catch (DataAccessException ex) {
            // Sin el INCR el contador quedaria una unidad abajo: se intenta borrarlo para que se recalcule
            log.warn("Redis no disponible al incrementar {}: {}", clave, ex.getMessage());
            invalidar(idUsuario);
        }
    }

    private void invalidar(Integer idUsuario) {
        String clave = ClavesRedis.notificacionesPendientes(idUsuario);
        try {
            redis.delete(clave);
        } catch (DataAccessException ex) {
            log.warn("Redis no disponible al borrar {}; se corregira cuando expire su TTL: {}", clave, ex.getMessage());
        }
    }

    // ---------- Correo ----------

    /** En segundo plano: ni un fallo ni la demora del servidor de correo llegan a la operacion que notifico. */
    private void enviarCorreo(Integer idUsuario, TipoNotificacion tipo, String correo, String titulo, String mensaje) {
        Runnable envio = () -> {
            try {
                correoService.enviar(correo, PREFIJO_ASUNTO + titulo, mensaje + PIE_CORREO);
            } catch (RuntimeException ex) {
                // Sin la direccion ni el mensaje crudo: solo el tipo de error
                log.warn("No se pudo enviar por correo la notificacion {} del usuario {}: {}", tipo, idUsuario,
                        ex.getClass().getSimpleName());
            }
        };
        try {
            correoExecutor.execute(envio);
        } catch (TaskRejectedException ex) {
            log.warn("Correo de la notificacion {} del usuario {} descartado: ejecutor saturado", tipo, idUsuario);
        }
    }

    /** Ejecuta la accion despues del commit; sin transaccion activa (p. ej. tests unitarios), al instante. */
    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
