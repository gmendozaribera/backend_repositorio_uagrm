package com.uagrm.repositorio.imp.service.plagio;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.AnalisisPlagioDocumento;
import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.FuentePlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.AnalisisPlagioRepository;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.mapper.PlagioMapper;
import com.uagrm.repositorio.dto.plagio.AnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoAnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoPlagioCache;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.EstadoPlagioCacheService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.notificacion.TextosNotificacion;
import com.uagrm.repositorio.imp.service.trabajo.AccesoTrabajos;
import com.uagrm.repositorio.imp.service.trabajo.ActorTrabajo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deteccion de plagio (CU14, contrato plagio-api.yaml). Logica comun a todos los motores.
 *
 * <p><b>Quien ve y hace que:</b> iniciar y ver resultados solo docentes asignados al grupo del trabajo y ADMIN.
 * Un estudiante recibe 403 (ademas lo corta el SecurityFilterChain por rol); cualquier otro usuario que no ve el
 * trabajo recibe 404, igual que un id inexistente (reutiliza {@link AccesoTrabajos}).</p>
 *
 * <p><b>Flujo:</b> {@link #iniciar} crea el analisis EN_PROCESO (202) y, despues del commit, guarda el estado en
 * Redis y pide al {@link DespachadorPlagio} (Quartz) que lo envie al {@link MotorPlagio} en segundo plano. El motor
 * entrega el resultado de cada documento con {@link #registrarDocumento}; cuando estan todos se completa:
 * porcentaje del trabajo = el MAXIMO de sus documentos (un solo documento copiado basta para que el trabajo sea
 * sospechoso; un promedio lo diluiria), nivel segun los umbrales VIGENTES de la facultad (se guardan los usados),
 * fuentes consolidadas (la misma fuente en dos documentos cuenta una vez, con su mayor porcentaje), ordenadas y
 * limitadas a PLAGIO_GROUP.MAX_FUENTES. Al terminar (COMPLETADO o ERROR) se notifica al docente que lo inicio.</p>
 *
 * <p>Todas las operaciones del motor son idempotentes: un resultado repetido, o que llega despues de un ERROR por
 * timeout, no cambia nada.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlagioService {

    /** Extensiones que se analizan (texto). Imagenes, videos, codigo y presentaciones no. */
    public static final Set<String> EXTENSIONES_ANALIZABLES = Set.of("pdf", "docx", "odt");
    /** Estados del trabajo que admiten un analisis (contrato: 409 en BORRADOR o RECHAZADO). */
    static final Set<EstadoTrabajo> ESTADOS_ANALIZABLES = EnumSet.of(EstadoTrabajo.PENDIENTE,
            EstadoTrabajo.EN_REVISION, EstadoTrabajo.APROBADO, EstadoTrabajo.APROBADO_CON_OBSERVACIONES,
            EstadoTrabajo.EVALUADO);

    static final String MENSAJE_EN_CURSO = "Ya hay un analisis de plagio en curso para este trabajo.";
    public static final String MENSAJE_TIMEOUT = "El analisis excedio el tiempo maximo.";
    public static final String MENSAJE_ENVIO =
            "No se pudo enviar el analisis al motor de deteccion. Intente nuevamente en unos minutos.";
    public static final String MENSAJE_PROVEEDOR = "El proveedor de deteccion no pudo analizar uno de los documentos.";
    public static final String MENSAJE_REINICIO = "El analisis se interrumpio por un reinicio del servicio.";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final AnalisisPlagioRepository analisisRepository;
    private final DocumentoRepository documentoRepository;
    private final TrabajoRepository trabajoRepository;
    private final AccesoTrabajos acceso;
    private final ConfigFacultadService configFacultad;
    private final ParametrosService parametros;
    private final NotificacionService notificaciones;
    private final EstadoPlagioCacheService cache;
    private final PlagioMapper mapper;
    private final ObjectProvider<MotorPlagio> motores;
    private final ObjectProvider<DespachadorPlagio> despachadores;

    // ---------- Endpoints ----------

    /**
     * POST /plagio/trabajos/{id}: 404 si no ve el trabajo; 409 si el estado no lo permite o ya hay uno en curso;
     * 400 sin archivos analizables; 503 si este servicio no tiene motor.
     */
    @Transactional
    public AnalisisPlagioResponse iniciar(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = actorDocenteOAdmin(jwt);
        // Bloquea el trabajo: dos inicios simultaneos se ejecutan uno tras otro y el segundo ve el EN_PROCESO
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        if (!ESTADOS_ANALIZABLES.contains(trabajo.getEstado())) {
            throw NegocioException.conflicto("El trabajo esta en estado " + trabajo.getEstado()
                    + " y no se puede analizar.");
        }
        MotorPlagio motor = motores.getIfAvailable();
        if (motor == null) {
            throw NegocioException.noDisponible("La deteccion de plagio no esta disponible en este momento.");
        }
        List<Documento> archivos = documentoRepository.findByTrabajoIdTrabajoAndTipoAndExtensionInOrderByFechaSubidaAscIdDocumentoAsc(
                idTrabajo, TipoDocumento.ARCHIVO, EXTENSIONES_ANALIZABLES);
        if (archivos.isEmpty()) {
            throw NegocioException.datosInvalidos(
                    "El trabajo no tiene archivos analizables: se analizan documentos PDF, DOCX u ODT.");
        }
        if (analisisRepository.existsByTrabajoIdTrabajoAndEstado(idTrabajo, EstadoAnalisisPlagio.EN_PROCESO)) {
            throw NegocioException.conflicto(MENSAJE_EN_CURSO);
        }

        ConfigFacultadResponse config = configFacultad.configDe(idFacultad(trabajo));
        // 400 ANTES de crear nada: sin fuentes de comparacion no se crea el analisis ni se gastan creditos
        motor.verificarFuentes(config.comparacionInternet());
        AnalisisPlagio analisis = new AnalisisPlagio();
        analisis.setTrabajo(trabajo);
        analisis.setEstado(EstadoAnalisisPlagio.EN_PROCESO);
        analisis.setMotor(motor.tipo());
        analisis.setUmbralMedio(config.umbralPlagioMedio());
        analisis.setUmbralAlto(config.umbralPlagioAlto());
        analisis.setComparacionInternet(config.comparacionInternet());
        analisis.setDocumentosAnalizados(archivos.size());
        analisis.setScanId(motor.tipo() == TipoMotorPlagio.COPYLEAKS ? nuevoScanId() : null);
        analisis.setUsuarioInicio(actor.usuario());
        analisis.setFechaInicio(Instant.now());
        int indice = 1;
        for (Documento archivo : archivos) {
            AnalisisPlagioDocumento doc = new AnalisisPlagioDocumento();
            doc.setAnalisis(analisis);
            doc.setIndice(indice++);
            doc.setIdDocumento(archivo.getIdDocumento());
            doc.setNombreArchivo(archivo.getNombreArchivo());
            doc.setRutaArchivo(archivo.getRutaArchivo());
            doc.setSha256(archivo.getSha256());
            analisis.getDocumentos().add(doc);
        }
        try {
            analisisRepository.saveAndFlush(analisis);
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad: el indice unico parcial (un solo EN_PROCESO por trabajo) rechazo al segundo
            throw NegocioException.conflicto(MENSAJE_EN_CURSO);
        }

        Integer idAnalisis = analisis.getIdAnalisis();
        EstadoPlagioCache estado = mapper.toCache(analisis);
        despuesDelCommit(() -> {
            cache.guardar(idTrabajo, estado);
            despachar(idAnalisis);
        });
        log.info("Analisis de plagio {} del trabajo {} iniciado por el usuario {} (motor {}, {} documentos)",
                idAnalisis, idTrabajo, actor.idUsuario(), motor.tipo(), archivos.size());
        return mapper.toResponse(analisis, Set.of());
    }

    /** GET /plagio/trabajos/{id}: el analisis mas reciente; 404 si nunca se analizo. */
    @Transactional(readOnly = true)
    public AnalisisPlagioResponse ultimo(Jwt jwt, Integer idTrabajo) {
        verificarAcceso(jwt, idTrabajo);
        AnalisisPlagio analisis = analisisRepository.findFirstByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(
                idTrabajo).orElseThrow(() -> sinAnalisis(idTrabajo));
        return responder(List.of(analisis)).getFirst();
    }

    /** GET /plagio/trabajos/{id}/historial: del mas reciente al mas antiguo (lista vacia si nunca se analizo). */
    @Transactional(readOnly = true)
    public List<AnalisisPlagioResponse> historial(Jwt jwt, Integer idTrabajo) {
        verificarAcceso(jwt, idTrabajo);
        return responder(analisisRepository.findByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(idTrabajo));
    }

    /**
     * GET /plagio/trabajos/{id}/estado (polling cada ~5 s). Los permisos se verifican SIEMPRE contra la BD (son 2-3
     * consultas por indice: quien pregunta y si es docente del grupo); el estado del analisis sale de Redis y solo
     * si falta (o Redis no responde) se lee de la BD y se vuelve a cachear.
     */
    @Transactional(readOnly = true)
    public EstadoAnalisisPlagioResponse estado(Jwt jwt, Integer idTrabajo) {
        verificarAcceso(jwt, idTrabajo);
        Optional<EstadoPlagioCache> enCache = cache.leer(idTrabajo);
        if (enCache.isPresent()) {
            return mapper.toEstado(enCache.get());
        }
        AnalisisPlagio analisis = analisisRepository.findFirstByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(
                idTrabajo).orElseThrow(() -> sinAnalisis(idTrabajo));
        EstadoPlagioCache estado = mapper.toCache(analisis);
        cache.guardar(idTrabajo, estado);
        return mapper.toEstado(estado);
    }

    // ---------- Uso de los motores y jobs (plagiarism-service) ----------

    /**
     * Datos para enviar el analisis al motor, solo con los documentos aun sin resultado. Vacio si el analisis no
     * existe o ya termino (p. ej. el timeout gano la carrera).
     */
    @Transactional(readOnly = true)
    public Optional<SolicitudAnalisis> solicitud(Integer idAnalisis) {
        return analisisRepository.findById(idAnalisis)
                .filter(analisis -> !analisis.terminado())
                .map(analisis -> new SolicitudAnalisis(analisis.getIdAnalisis(), analisis.getTrabajo().getIdTrabajo(),
                        idFacultad(analisis.getTrabajo()), analisis.getScanId(), analisis.getComparacionInternet(),
                        analisis.getDocumentos().stream()
                                .filter(doc -> !doc.procesado())
                                .map(doc -> new SolicitudAnalisis.DocumentoAnalisis(doc.getIndice(),
                                        doc.getNombreArchivo(), doc.getRutaArchivo(), doc.getSha256()))
                                .toList()));
    }

    /**
     * Resultado de UN documento. Idempotente: si el analisis ya termino o el documento ya tiene resultado, no
     * cambia nada. Cuando todos los documentos tienen resultado, completa el analisis.
     *
     * @param fuentes coincidencias del documento (se ignoran las que apuntan al propio trabajo)
     * @throws NegocioException 404 si el analisis o el documento (indice) no existen
     */
    @Transactional
    public void registrarDocumento(Integer idAnalisis, int indice, BigDecimal porcentaje,
                                   List<FuenteDetectada> fuentes) {
        AnalisisPlagio analisis = analisisRepository.findParaActualizar(idAnalisis)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el analisis " + idAnalisis + "."));
        if (analisis.terminado()) {
            log.info("Resultado del documento {} del analisis {} ignorado: el analisis ya esta {}", indice,
                    idAnalisis, analisis.getEstado());
            return;
        }
        AnalisisPlagioDocumento documento = analisis.getDocumentos().stream()
                .filter(doc -> doc.getIndice() == indice)
                .findFirst()
                .orElseThrow(() -> NegocioException.noEncontrado(
                        "El analisis " + idAnalisis + " no tiene el documento " + indice + "."));
        if (documento.procesado()) {
            log.info("Resultado repetido del documento {} del analisis {}: ignorado", indice, idAnalisis);
            return;
        }
        documento.setPorcentaje(acotar(porcentaje));
        documento.setFechaResultado(Instant.now());

        Integer idPropio = analisis.getTrabajo().getIdTrabajo();
        int maximo = parametros.entero(Parametro.PLAGIO_MAX_FUENTES);
        fuentes.stream()
                .filter(f -> !(f.tipo() == TipoFuentePlagio.INTERNA && Objects.equals(f.idTrabajoFuente(), idPropio)))
                .sorted(Comparator.comparing(FuenteDetectada::porcentaje, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maximo)
                .forEach(f -> analisis.getFuentes().add(fuente(analisis, f)));

        if (analisis.getDocumentos().stream().allMatch(AnalisisPlagioDocumento::procesado)) {
            completar(analisis, maximo);
        }
    }

    /**
     * Pasa el analisis a ERROR con un motivo legible (sin datos internos). Idempotente.
     *
     * @return true si cambio (estaba EN_PROCESO)
     */
    @Transactional
    public boolean fallar(Integer idAnalisis, String mensaje) {
        return fallarBloqueado(idAnalisis, mensaje);
    }

    /**
     * Job de timeout: pasa a ERROR los analisis EN_PROCESO iniciados hace mas de PLAGIO_GROUP.TIMEOUT_MINUTOS.
     *
     * @return cantidad de analisis vencidos
     */
    @Transactional
    public int marcarExcedidos() {
        int minutos = parametros.entero(Parametro.PLAGIO_TIMEOUT_MINUTOS);
        Instant limite = Instant.now().minus(Duration.ofMinutes(minutos));
        int vencidos = 0;
        for (Integer id : analisisRepository.findIdsPorEstadoIniciadosAntesDe(EstadoAnalisisPlagio.EN_PROCESO, limite)) {
            if (fallarBloqueado(id, MENSAJE_TIMEOUT)) {
                vencidos++;
            }
        }
        if (vencidos > 0) {
            log.warn("{} analisis de plagio excedieron {} minutos y pasaron a ERROR", vencidos, minutos);
        }
        return vencidos;
    }

    /** Ids de los analisis EN_PROCESO de un motor (al arrancar, para reanudarlos). */
    @Transactional(readOnly = true)
    public List<Integer> enProceso(TipoMotorPlagio motor) {
        return analisisRepository.findIdsPorEstadoYMotor(EstadoAnalisisPlagio.EN_PROCESO, motor);
    }

    /** Id del analisis con ese prefijo de scan (webhook de Copyleaks). */
    @Transactional(readOnly = true)
    public Optional<Integer> analisisPorScan(String scanId) {
        return analisisRepository.findIdByScanId(scanId);
    }

    /** Trabajo analizado con ese prefijo de scan (para identificar fuentes internas que devuelve Copyleaks). */
    @Transactional(readOnly = true)
    public Optional<Integer> trabajoPorScan(String scanId) {
        return analisisRepository.findIdTrabajoByScanId(scanId);
    }

    // ---------- privados ----------

    private ActorTrabajo actorDocenteOAdmin(Jwt jwt) {
        ActorTrabajo actor = acceso.actor(jwt);
        if (!actor.esDocente() && !actor.esAdmin()) {
            throw NegocioException.accesoDenegado(
                    "Solo los docentes del grupo y el administrador pueden ver o iniciar analisis de plagio.");
        }
        return actor;
    }

    /** 403 si es estudiante; 404 si no ve el trabajo (un docente ve solo los trabajos de sus grupos). */
    private void verificarAcceso(Jwt jwt, Integer idTrabajo) {
        acceso.paraLeer(idTrabajo, actorDocenteOAdmin(jwt));
    }

    private List<AnalisisPlagioResponse> responder(List<AnalisisPlagio> analisis) {
        List<Integer> internas = analisis.stream()
                .flatMap(a -> a.getFuentes().stream())
                .filter(f -> f.getTipo() == TipoFuentePlagio.INTERNA && f.getIdTrabajoFuente() != null)
                .map(FuentePlagio::getIdTrabajoFuente)
                .distinct()
                .toList();
        Set<Integer> publicados = internas.isEmpty()
                ? Set.of() : trabajoRepository.findIdsEnEstado(internas, EstadoTrabajo.EVALUADO);
        return analisis.stream().map(a -> mapper.toResponse(a, publicados)).toList();
    }

    private void completar(AnalisisPlagio analisis, int maximoFuentes) {
        BigDecimal porcentaje = analisis.getDocumentos().stream()
                .map(AnalisisPlagioDocumento::getPorcentaje)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO.setScale(2));
        aplicarUmbralesVigentes(analisis);
        analisis.setPorcentaje(porcentaje);
        analisis.setNivel(NivelPlagio.de(porcentaje, analisis.getUmbralMedio(), analisis.getUmbralAlto()));
        analisis.setEstado(EstadoAnalisisPlagio.COMPLETADO);
        analisis.setMensajeError(null);
        analisis.setFechaFin(Instant.now());
        analisis.getFuentes().retainAll(consolidar(analisis.getFuentes(), maximoFuentes));
        terminar(analisis);
    }

    /**
     * La misma fuente encontrada en varios documentos cuenta una vez, con su mayor porcentaje; despues se dejan las
     * de mayor porcentaje hasta el maximo.
     */
    static List<FuentePlagio> consolidar(Collection<FuentePlagio> fuentes, int maximo) {
        Map<String, FuentePlagio> porClave = new LinkedHashMap<>();
        for (FuentePlagio fuente : fuentes) {
            porClave.merge(clave(fuente), fuente,
                    (actual, nueva) -> nueva.getPorcentaje().compareTo(actual.getPorcentaje()) > 0 ? nueva : actual);
        }
        return porClave.values().stream()
                .sorted(Comparator.comparing(FuentePlagio::getPorcentaje).reversed()
                        .thenComparing(FuentePlagio::getTitulo))
                .limit(maximo)
                .toList();
    }

    private static String clave(FuentePlagio fuente) {
        if (fuente.getTipo() == TipoFuentePlagio.INTERNA) {
            return fuente.getIdTrabajoFuente() != null
                    ? "trabajo:" + fuente.getIdTrabajoFuente() : "interna:" + fuente.getTitulo();
        }
        return fuente.getUrl() != null ? "url:" + fuente.getUrl() : "internet:" + fuente.getTitulo();
    }

    /** ERROR dentro de la transaccion actual, con el analisis bloqueado. */
    private boolean fallarBloqueado(Integer idAnalisis, String mensaje) {
        AnalisisPlagio analisis = analisisRepository.findParaActualizar(idAnalisis).orElse(null);
        if (analisis == null || analisis.terminado()) {
            return false;
        }
        aplicarUmbralesVigentes(analisis);
        analisis.setEstado(EstadoAnalisisPlagio.ERROR);
        analisis.setPorcentaje(null);
        analisis.setNivel(null);
        analisis.setMensajeError(mensaje);
        analisis.setFechaFin(Instant.now());
        terminar(analisis);
        log.warn("Analisis de plagio {} en ERROR: {}", idAnalisis, mensaje);
        return true;
    }

    /** Umbrales de la facultad vigentes AL TERMINAR (pueden haber cambiado desde que se inicio). */
    private void aplicarUmbralesVigentes(AnalisisPlagio analisis) {
        ConfigFacultadResponse config = configFacultad.configDe(idFacultad(analisis.getTrabajo()));
        analisis.setUmbralMedio(config.umbralPlagioMedio());
        analisis.setUmbralAlto(config.umbralPlagioAlto());
    }

    /** Notifica al docente que lo inicio y, tras el commit, actualiza el estado en Redis. */
    private void terminar(AnalisisPlagio analisis) {
        Trabajo trabajo = analisis.getTrabajo();
        boolean completado = analisis.getEstado() == EstadoAnalisisPlagio.COMPLETADO;
        String mensaje = completado
                ? TextosNotificacion.MENSAJE_PLAGIO_COMPLETADO.formatted(trabajo.getTitulo(), nivelLegible(analisis.getNivel()))
                : TextosNotificacion.MENSAJE_PLAGIO_ERROR.formatted(trabajo.getTitulo());
        notificaciones.notificar(analisis.getUsuarioInicio().getIdUsuario(), TipoNotificacion.PLAGIO_COMPLETADO,
                completado ? TextosNotificacion.TITULO_PLAGIO_COMPLETADO : TextosNotificacion.TITULO_PLAGIO_ERROR,
                mensaje, TextosNotificacion.enlaceTrabajo(trabajo.getIdTrabajo()));
        Integer idTrabajo = trabajo.getIdTrabajo();
        EstadoPlagioCache estado = mapper.toCache(analisis);
        despuesDelCommit(() -> cache.guardar(idTrabajo, estado));
    }

    static String nivelLegible(NivelPlagio nivel) {
        return switch (nivel) {
            case OK -> "BAJO (verde)";
            case MEDIO -> "MEDIO (amarillo)";
            case ALTO -> "ALTO (rojo)";
        };
    }

    private static FuentePlagio fuente(AnalisisPlagio analisis, FuenteDetectada detectada) {
        FuentePlagio fuente = new FuentePlagio();
        fuente.setAnalisis(analisis);
        fuente.setTipo(detectada.tipo());
        fuente.setTitulo(recortar(detectada.titulo() == null || detectada.titulo().isBlank()
                ? (detectada.tipo() == TipoFuentePlagio.INTERNET ? "Pagina de internet" : PlagioMapper.TITULO_NO_PUBLICADO)
                : detectada.titulo(), 300));
        fuente.setUrl(detectada.tipo() == TipoFuentePlagio.INTERNET ? recortar(detectada.url(), 1000) : null);
        fuente.setIdTrabajoFuente(detectada.tipo() == TipoFuentePlagio.INTERNA ? detectada.idTrabajoFuente() : null);
        fuente.setPorcentaje(acotar(detectada.porcentaje()));
        return fuente;
    }

    /** 0..100 con 2 decimales (NUMERIC(5,2)); null cuenta como 0. */
    static BigDecimal acotar(BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.signum() < 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return porcentaje.min(CIEN).setScale(2, RoundingMode.HALF_UP);
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo);
    }

    private void despachar(Integer idAnalisis) {
        DespachadorPlagio despachador = despachadores.getIfAvailable();
        if (despachador == null) {
            log.error("Sin despachador de plagio en este servicio: el analisis {} vencera por timeout", idAnalisis);
            return;
        }
        try {
            despachador.despachar(idAnalisis);
        } catch (RuntimeException ex) {
            // Ya se respondio 202 (esto corre despues del commit): el job de timeout lo pasara a ERROR
            log.error("No se pudo agendar el envio del analisis {}: {}", idAnalisis, ex.getMessage());
        }
    }

    private static Integer idFacultad(Trabajo trabajo) {
        return trabajo.getMateria().getFacultad().getIdFacultad();
    }

    /** "uagrm-" + 20 hexadecimales aleatorios (26 caracteres; con "-{indice}" queda bajo el limite de 36). */
    static String nuevoScanId() {
        byte[] bytes = new byte[10];
        ALEATORIO.nextBytes(bytes);
        return "uagrm-" + HexFormat.of().formatHex(bytes);
    }

    private static NegocioException sinAnalisis(Integer idTrabajo) {
        return NegocioException.noEncontrado("El trabajo " + idTrabajo + " no tiene analisis de plagio.");
    }

    /** Ejecuta la accion despues del commit; sin transaccion activa (tests unitarios), al instante. */
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
