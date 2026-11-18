package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TrabajoHistorial;
import com.uagrm.repositorio.dao.entity.TransicionTrabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.GrupoRepository;
import com.uagrm.repositorio.dao.repository.HashtagRepository;
import com.uagrm.repositorio.dao.repository.TipoTrabajoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dao.specification.TrabajoSpecs;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.TrabajoMapper;
import com.uagrm.repositorio.dto.trabajo.AprobarRequest;
import com.uagrm.repositorio.dto.trabajo.RechazarRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoRequest;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResumenResponse;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.documento.ArchivosTrabajo;
import com.uagrm.repositorio.imp.service.documento.ReglasDocumentos;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.notificacion.TextosNotificacion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trabajos academicos: creacion por el estudiante, edicion por sus autores, envio a revision y revision por los
 * docentes del grupo. Todas las transiciones pasan por {@link TransicionTrabajo} (maquina de estados) y quedan en
 * el historial; quien ve y hace que lo decide {@link AccesoTrabajos}.
 */
@Service
@RequiredArgsConstructor
public class TrabajoService {

    /** "Mis trabajos": ultima actualizacion primero. */
    private static final Sort ORDEN_MIOS = Sort.by(Sort.Direction.DESC, "fechaActualizacion", "idTrabajo");
    /** Bandeja de revision: lo que espera hace mas tiempo primero. */
    private static final Sort ORDEN_REVISION = Sort.by(Sort.Direction.ASC, "fechaEnvio", "idTrabajo");
    /** Sin filtro de estado, la bandeja muestra lo que falta revisar. */
    private static final Set<EstadoTrabajo> POR_REVISAR = Set.of(EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION);

    private final TrabajoRepository trabajoRepository;
    private final GrupoRepository grupoRepository;
    private final TipoTrabajoRepository tipoTrabajoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HashtagRepository hashtagRepository;
    private final DocumentoRepository documentoRepository;
    private final TrabajoHistorialRepository historialRepository;
    private final GrupoDocenteRepository grupoDocenteRepository;
    private final AccesoTrabajos acceso;
    private final ArchivosTrabajo archivos;
    private final ParametrosService parametros;
    private final NotificacionService notificaciones;
    private final TrabajoMapper mapper;

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public PaginaResponse<TrabajoResumenResponse> listarMios(Jwt jwt, EstadoTrabajo estado, int pagina, int tamano) {
        ActorTrabajo actor = acceso.actor(jwt);
        if (!actor.esEstudiante()) {
            throw NegocioException.accesoDenegado("Solo los estudiantes tienen trabajos propios.");
        }
        Specification<Trabajo> spec = TrabajoSpecs.deAutor(actor.idPersona()).and(TrabajoSpecs.estado(estado));
        return pagina(spec, PageRequest.of(pagina, tamano, ORDEN_MIOS));
    }

    /** Bandeja: trabajos de los grupos del docente (ADMIN: todos). Nunca BORRADOR. */
    @Transactional(readOnly = true)
    public PaginaResponse<TrabajoResumenResponse> listarRevision(Jwt jwt, EstadoTrabajo estado, Integer idGrupo,
                                                                 int pagina, int tamano) {
        ActorTrabajo actor = acceso.actor(jwt);
        PageRequest pageable = PageRequest.of(pagina, tamano, ORDEN_REVISION);
        if (!actor.esDocente() && !actor.esAdmin()) {
            throw NegocioException.accesoDenegado("Solo docentes y administradores tienen bandeja de revision.");
        }
        if (estado == EstadoTrabajo.BORRADOR) {
            return new PaginaResponse<>(List.of(), pagina, tamano, 0, 0);  // un borrador nunca se revisa
        }
        Specification<Trabajo> spec = (estado == null ? TrabajoSpecs.estadoEn(POR_REVISAR) : TrabajoSpecs.estado(estado))
                .and(TrabajoSpecs.grupo(idGrupo));
        if (!actor.esAdmin()) {
            spec = spec.and(TrabajoSpecs.deGruposDelDocente(actor.idUsuario()));
        }
        return pagina(spec, pageable);
    }

    @Transactional(readOnly = true)
    public TrabajoResponse obtener(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        return detalle(acceso.paraLeer(idTrabajo, actor), actor);
    }

    // ---------- Autores ----------

    @Transactional
    public TrabajoResponse crear(Jwt jwt, TrabajoRequest request) {
        ActorTrabajo actor = acceso.actor(jwt);
        if (!actor.esEstudiante()) {
            throw NegocioException.accesoDenegado("Solo un estudiante puede crear trabajos.");
        }
        Instant ahora = Instant.now();
        Trabajo trabajo = new Trabajo();
        trabajo.setCreador(actor.persona());
        trabajo.setEstado(EstadoTrabajo.BORRADOR);
        trabajo.setFechaCreacion(ahora);
        trabajo.setFechaActualizacion(ahora);
        asignarGrupo(trabajo, request.idGrupo());
        asignarDatos(trabajo, request, actor);
        trabajoRepository.save(trabajo);
        registrarHistorial(trabajo, null, actor, null, ahora);
        return detalle(trabajo, actor);
    }

    /** Reemplaza datos, hashtags y coautores. El grupo solo cambia en BORRADOR. */
    @Transactional
    public TrabajoResponse actualizar(Jwt jwt, Integer idTrabajo, TrabajoRequest request) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirAutorEditable(trabajo, actor);
        if (!Objects.equals(trabajo.getGrupo().getIdGrupo(), request.idGrupo())) {
            if (trabajo.getEstado() != EstadoTrabajo.BORRADOR) {
                throw NegocioException.conflicto("El grupo solo se puede cambiar mientras el trabajo esta en BORRADOR.");
            }
            asignarGrupo(trabajo, request.idGrupo());
        }
        asignarDatos(trabajo, request, actor);
        trabajo.setFechaActualizacion(Instant.now());
        return detalle(trabajo, actor);
    }

    /** Borrado fisico (solo BORRADOR). Los archivos de MinIO se borran despues del commit. */
    @Transactional
    public void eliminar(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirAutor(trabajo, actor);
        if (trabajo.getEstado() != EstadoTrabajo.BORRADOR) {
            throw NegocioException.conflicto("Solo se puede eliminar un trabajo en estado BORRADOR.");
        }
        List<String> rutas = documentoRepository.findRutasByTrabajo(idTrabajo);
        trabajoRepository.delete(trabajo);  // documentos e historial caen por ON DELETE CASCADE
        archivos.borrarTrasCommit(rutas);
    }

    /** A PENDIENTE; exige al menos un documento PDF. Avisa a los docentes del grupo. */
    @Transactional
    public TrabajoResponse enviar(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirAutor(trabajo, actor);
        exigirTransicion(trabajo, TransicionTrabajo.ENVIAR);
        if (!documentoRepository.existsByTrabajoIdTrabajoAndTipoAndExtension(idTrabajo, TipoDocumento.ARCHIVO,
                ReglasDocumentos.EXTENSION_PDF)) {
            throw NegocioException.datosInvalidos("Para enviar el trabajo debe subir al menos un documento PDF.");
        }
        Instant ahora = aplicar(trabajo, TransicionTrabajo.ENVIAR, actor, null);
        trabajo.setFechaEnvio(ahora);
        Grupo grupo = trabajo.getGrupo();
        notificar(grupoDocenteRepository.findIdsDocentes(grupo.getIdGrupo()), actor, trabajo,
                TipoNotificacion.TRABAJO_ENVIADO, TextosNotificacion.TITULO_TRABAJO_ENVIADO,
                TextosNotificacion.MENSAJE_TRABAJO_ENVIADO.formatted(trabajo.getTitulo(), grupo.getNombre(),
                        trabajo.getMateria().getSigla()));
        return detalle(trabajo, actor);
    }

    // ---------- Docentes del grupo ----------

    @Transactional
    public TrabajoResponse tomarRevision(Jwt jwt, Integer idTrabajo) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirDocenteDelGrupo(trabajo, actor);
        exigirTransicion(trabajo, TransicionTrabajo.TOMAR_REVISION);
        aplicar(trabajo, TransicionTrabajo.TOMAR_REVISION, actor, null);
        notificarAutores(trabajo, actor, TipoNotificacion.TRABAJO_EN_REVISION,
                TextosNotificacion.TITULO_TRABAJO_EN_REVISION, TextosNotificacion.MENSAJE_TRABAJO_EN_REVISION);
        return detalle(trabajo, actor);
    }

    /** Sin observaciones → APROBADO; con texto → APROBADO_CON_OBSERVACIONES. */
    @Transactional
    public TrabajoResponse aprobar(Jwt jwt, Integer idTrabajo, AprobarRequest request) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirDocenteDelGrupo(trabajo, actor);
        String observaciones = request == null || request.observaciones() == null || request.observaciones().isBlank()
                ? null : request.observaciones().strip();
        TransicionTrabajo transicion = observaciones == null
                ? TransicionTrabajo.APROBAR : TransicionTrabajo.APROBAR_CON_OBSERVACIONES;
        exigirTransicion(trabajo, transicion);
        trabajo.setObservaciones(observaciones);
        aplicar(trabajo, transicion, actor, observaciones);
        if (observaciones == null) {
            notificarAutores(trabajo, actor, TipoNotificacion.TRABAJO_APROBADO,
                    TextosNotificacion.TITULO_TRABAJO_APROBADO, TextosNotificacion.MENSAJE_TRABAJO_APROBADO);
        } else {
            notificarAutores(trabajo, actor, TipoNotificacion.TRABAJO_APROBADO_CON_OBSERVACIONES,
                    TextosNotificacion.TITULO_TRABAJO_APROBADO_CON_OBSERVACIONES,
                    TextosNotificacion.MENSAJE_TRABAJO_APROBADO_CON_OBSERVACIONES);
        }
        return detalle(trabajo, actor);
    }

    @Transactional
    public TrabajoResponse rechazar(Jwt jwt, Integer idTrabajo, RechazarRequest request) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirDocenteDelGrupo(trabajo, actor);
        exigirTransicion(trabajo, TransicionTrabajo.RECHAZAR);
        String motivo = request.motivo().strip();
        trabajo.setObservaciones(motivo);
        aplicar(trabajo, TransicionTrabajo.RECHAZAR, actor, motivo);
        notificarAutores(trabajo, actor, TipoNotificacion.TRABAJO_RECHAZADO,
                TextosNotificacion.TITULO_TRABAJO_RECHAZADO, TextosNotificacion.MENSAJE_TRABAJO_RECHAZADO);
        return detalle(trabajo, actor);
    }

    // ---------- Maquina de estados ----------

    /** 409 si la transicion no se puede aplicar desde el estado actual. */
    static void exigirTransicion(Trabajo trabajo, TransicionTrabajo transicion) {
        if (!transicion.aplicableDesde(trabajo.getEstado())) {
            throw NegocioException.conflicto("No se puede " + verbo(transicion) + " un trabajo en estado "
                    + trabajo.getEstado() + ".");
        }
    }

    private Instant aplicar(Trabajo trabajo, TransicionTrabajo transicion, ActorTrabajo actor, String comentario) {
        EstadoTrabajo anterior = trabajo.getEstado();
        Instant ahora = Instant.now();
        trabajo.setEstado(transicion.destino());
        trabajo.setFechaActualizacion(ahora);
        registrarHistorial(trabajo, anterior, actor, comentario, ahora);
        return ahora;
    }

    private static String verbo(TransicionTrabajo transicion) {
        return switch (transicion) {
            case ENVIAR -> "enviar";
            case TOMAR_REVISION -> "tomar en revision";
            case APROBAR, APROBAR_CON_OBSERVACIONES -> "aprobar";
            case RECHAZAR -> "rechazar";
        };
    }

    private void registrarHistorial(Trabajo trabajo, EstadoTrabajo anterior, ActorTrabajo actor, String comentario,
                                    Instant fecha) {
        TrabajoHistorial historial = new TrabajoHistorial();
        historial.setTrabajo(trabajo);
        historial.setEstadoAnterior(anterior);
        historial.setEstadoNuevo(trabajo.getEstado());
        historial.setUsuario(actor.usuario());
        historial.setComentario(comentario);
        historial.setFecha(fecha);
        historialRepository.save(historial);
    }

    // ---------- Datos del trabajo ----------

    /** Grupo (404 si no existe, 409 si esta inactivo); materia y semestre salen del grupo. */
    private void asignarGrupo(Trabajo trabajo, Integer idGrupo) {
        Grupo grupo = grupoRepository.findById(idGrupo)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el grupo " + idGrupo + "."));
        if (!Objects.equals(grupo.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("El grupo " + grupo.getNombre() + " esta inactivo.");
        }
        trabajo.setGrupo(grupo);
        trabajo.setMateria(grupo.getMateria());
        trabajo.setSemestre(grupo.getSemestre());
    }

    private void asignarDatos(Trabajo trabajo, TrabajoRequest request, ActorTrabajo actor) {
        trabajo.setTitulo(request.titulo().strip());
        trabajo.setResumen(request.resumen().strip());
        asignarTipo(trabajo, request.idTipoTrabajo());
        asignarAutores(trabajo, actor, request.registrosCoautores());
        asignarHashtags(trabajo, request.hashtags());
    }

    /** Tipo de trabajo activo y de la facultad de la materia (conservar el que ya tenia siempre se permite). */
    private void asignarTipo(Trabajo trabajo, Integer idTipoTrabajo) {
        TipoTrabajo actual = trabajo.getTipoTrabajo();
        Integer idFacultad = trabajo.getMateria().getFacultad().getIdFacultad();
        if (actual != null && Objects.equals(actual.getIdTipoTrabajo(), idTipoTrabajo)
                && Objects.equals(actual.getFacultad().getIdFacultad(), idFacultad)) {
            return;
        }
        TipoTrabajo tipo = tipoTrabajoRepository.findById(idTipoTrabajo).orElseThrow(() ->
                NegocioException.noEncontrado("No existe el tipo de trabajo " + idTipoTrabajo + "."));
        if (!Objects.equals(tipo.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("El tipo de trabajo " + tipo.getNombre() + " esta inactivo.");
        }
        if (!Objects.equals(tipo.getFacultad().getIdFacultad(), idFacultad)) {
            throw NegocioException.campoInvalido("idTipoTrabajo", "no pertenece a la facultad de la materia del grupo");
        }
        trabajo.setTipoTrabajo(tipo);
    }

    /**
     * Autores = el estudiante autenticado (nunca puede quitarse a si mismo) + los coautores por registro, que deben
     * ser cuentas ESTUDIANTE activas. Total <= WORK_GROUP.MAX_AUTORES.
     */
    private void asignarAutores(Trabajo trabajo, ActorTrabajo actor, List<Integer> registrosCoautores) {
        Set<Integer> registros = new LinkedHashSet<>(registrosCoautores == null ? List.of() : registrosCoautores);
        registros.remove(actor.persona().getRegistro());
        int maximo = parametros.entero(Parametro.WORK_MAX_AUTORES);
        if (registros.size() + 1 > maximo) {
            throw NegocioException.campoInvalido("registrosCoautores",
                    "un trabajo admite como maximo " + maximo + " autores, contandolo a usted");
        }
        Map<Integer, Usuario> porRegistro = registros.isEmpty() ? Map.of()
                : usuarioRepository.findByPersonaRegistroIn(registros).stream()
                        .collect(Collectors.toMap(u -> u.getPersona().getRegistro(), Function.identity()));
        List<Integer> invalidos = new ArrayList<>();
        Set<Persona> autores = new LinkedHashSet<>();
        autores.add(actor.persona());
        for (Integer registro : registros) {
            Usuario usuario = porRegistro.get(registro);
            if (usuario == null || !TipoUsuario.ESTUDIANTE.equals(usuario.getTipoUsuario().getNombre())
                    || !Objects.equals(usuario.getEstado(), Estado.ACTIVO)) {
                invalidos.add(registro);
            } else {
                autores.add(usuario.getPersona());
            }
        }
        if (!invalidos.isEmpty()) {
            throw NegocioException.campoInvalido("registrosCoautores", "no corresponden a estudiantes activos: "
                    + invalidos.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        trabajo.getAutores().clear();
        trabajo.getAutores().addAll(autores);
    }

    /**
     * Minusculas y sin repetidos; reutiliza las filas existentes. Las nuevas se crean con ON CONFLICT DO NOTHING:
     * si otro request crea el mismo hashtag a la vez, ninguno de los dos falla.
     */
    private void asignarHashtags(Trabajo trabajo, List<String> hashtags) {
        Set<String> textos = (hashtags == null ? List.<String>of() : hashtags).stream()
                .map(h -> h.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        trabajo.getHashtags().clear();
        if (textos.isEmpty()) {
            return;
        }
        textos.forEach(hashtagRepository::crearSiNoExiste);
        trabajo.getHashtags().addAll(hashtagRepository.findByTextoIn(textos));
    }

    // ---------- Respuestas y notificaciones ----------

    private PaginaResponse<TrabajoResumenResponse> pagina(Specification<Trabajo> spec, PageRequest pageable) {
        Page<Trabajo> page = trabajoRepository.findAll(spec, pageable);
        return PaginaResponse.de(page, mapper::toResumen);
    }

    private TrabajoResponse detalle(Trabajo trabajo, ActorTrabajo actor) {
        Integer id = trabajo.getIdTrabajo();
        return mapper.toResponse(trabajo,
                documentoRepository.findByTrabajoIdTrabajoOrderByFechaSubidaAscIdDocumentoAsc(id),
                historialRepository.findByTrabajoIdTrabajoOrderByFechaAscIdHistorialAsc(id),
                acceso.acciones(trabajo, acceso.permisos(trabajo, actor)), actor.idPersona());
    }

    private void notificarAutores(Trabajo trabajo, ActorTrabajo actor, TipoNotificacion tipo, String titulo,
                                  String plantilla) {
        List<Integer> idsPersona = trabajo.getAutores().stream().map(Persona::getIdPersona).toList();
        notificar(usuarioRepository.findIdsByPersonas(idsPersona), actor, trabajo, tipo, titulo,
                plantilla.formatted(trabajo.getTitulo()));
    }

    /** Notifica a cada destinatario menos al propio actor (nadie recibe aviso de lo que el mismo hizo). */
    private void notificar(Collection<Integer> idsUsuario, ActorTrabajo actor, Trabajo trabajo, TipoNotificacion tipo,
                           String titulo, String mensaje) {
        String enlace = TextosNotificacion.enlaceTrabajo(trabajo.getIdTrabajo());
        new LinkedHashSet<>(idsUsuario).stream()
                .filter(id -> !Objects.equals(id, actor.idUsuario()))
                .forEach(id -> notificaciones.notificar(id, tipo, titulo, mensaje, enlace));
    }
}
