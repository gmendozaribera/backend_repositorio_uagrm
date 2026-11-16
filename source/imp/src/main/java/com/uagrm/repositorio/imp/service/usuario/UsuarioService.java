package com.uagrm.repositorio.imp.service.usuario;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.repository.TipoUsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dao.specification.UsuarioSpecs;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.UsuarioMapper;
import com.uagrm.repositorio.dto.usuario.ActualizarUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.CrearUsuarioRequest;
import com.uagrm.repositorio.dto.usuario.RestablecerPasswordRequest;
import com.uagrm.repositorio.dto.usuario.TipoUsuarioResponse;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.RevocacionTokenService;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.notificacion.TextosNotificacion;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion de usuarios por el ADMIN (personas + cuentas). Sin borrado fisico: desactivar = estado 0.
 * "Cerrar sesiones" reutiliza la revocacion de auth (auth:revocado-desde:{idUsuario}): todo token emitido
 * antes de ese instante deja de valer en todos los microservicios. Se ejecuta dentro de la transaccion:
 * si Redis no responde (503) el cambio en la BD se revierte, asi nunca queda un usuario desactivado
 * con sesiones vivas.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Sort ORDEN = Sort.by("persona.apellidos", "persona.nombre", "idUsuario");

    private final UsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final CarreraRepository carreraRepository;
    private final PasswordEncoder passwordEncoder;
    private final RevocacionTokenService revocacionTokenService;
    private final UsuarioMapper mapper;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(String q, String rol, Integer idCarrera, boolean incluirInactivos,
                                                  int pagina, int tamano) {
        return PaginaResponse.de(usuarioRepository.findAll(
                CatalogoSpecs.<Usuario>activos(incluirInactivos)
                        .and(UsuarioSpecs.texto(q))
                        .and(UsuarioSpecs.rol(rol))
                        .and(UsuarioSpecs.carrera(idCarrera)),
                PageRequest.of(pagina, tamano, ORDEN)), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Integer id) {
        return mapper.toResponse(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        String login = request.usuario().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByUsuario(login)) {
            throw NegocioException.conflicto("Ya existe el usuario " + login + ".");
        }
        TipoUsuario rol = rol(request.rol());
        exigirDatosDeEstudiante(rol, request.registro(), request.idCarrera());

        String correo = correo(request.correo());
        String ci = CarnetIdentidad.normalizar(request.ci());
        validarDuplicados(ci, correo, request.registro(), 0);
        Persona persona = new Persona();
        mapper.actualizar(request, persona);
        normalizar(persona, ci, correo);
        persona.setCarrera(carrera(request.idCarrera(), null));
        personaRepository.save(persona);

        Usuario usuario = new Usuario();
        usuario.setUsuario(login);
        usuario.setPassword(passwordEncoder.encode(request.passwordInicial()));
        usuario.setRequiereCambioPassword(Boolean.TRUE);
        usuario.setTwoFactorEnabled(Boolean.FALSE);
        usuario.setTipoUsuario(rol);
        usuario.setPersona(persona);
        usuario.setEstado(Estado.ACTIVO);
        Usuario guardado = usuarioRepository.save(usuario);
        notificacionService.notificar(guardado.getIdUsuario(), TipoNotificacion.BIENVENIDA,
                TextosNotificacion.TITULO_BIENVENIDA, TextosNotificacion.MENSAJE_BIENVENIDA_ADMIN,
                TextosNotificacion.ENLACE_SEGURIDAD);
        return mapper.toResponse(guardado);
    }

    /**
     * El login no cambia. registro e idCarrera null conservan el valor actual: un ESTUDIANTE siempre queda con
     * carrera y registro (400 si al pasar a ESTUDIANTE no los tiene). Cambiar el rol o desactivar cierra todas
     * las sesiones del usuario.
     */
    @Transactional
    public UsuarioResponse actualizar(Jwt admin, Integer id, ActualizarUsuarioRequest request) {
        Usuario usuario = buscar(id);
        TipoUsuario rol = rol(request.rol());
        boolean cambiaRol = !Objects.equals(rol.getNombre(), usuario.getTipoUsuario().getNombre());
        boolean desactiva = Objects.equals(request.estado(), Estado.INACTIVO)
                && Objects.equals(usuario.getEstado(), Estado.ACTIVO);
        if (esElMismo(admin, usuario)) {
            if (Objects.equals(request.estado(), Estado.INACTIVO)) {
                throw datosInvalidos("No puede desactivar su propia cuenta.");
            }
            if (cambiaRol) {
                throw datosInvalidos("No puede quitarse el rol ADMIN a si mismo.");
            }
        }
        // Valores que quedarian despues del cambio (null = se conserva el actual), validados antes de tocar la entidad
        Persona persona = usuario.getPersona();
        Carrera carreraActual = persona.getCarrera();
        Integer registroFinal = request.registro() != null ? request.registro() : persona.getRegistro();
        Integer idCarreraFinal = request.idCarrera() != null ? request.idCarrera()
                : carreraActual == null ? null : carreraActual.getIdCarrera();
        exigirDatosDeEstudiante(rol, registroFinal, idCarreraFinal);

        // Duplicados antes de tocar la entidad: la consulta no debe disparar un flush con datos repetidos
        String correo = correo(request.correo());
        String ci = CarnetIdentidad.normalizar(request.ci());
        validarDuplicados(ci, correo, request.registro(), persona.getIdPersona());
        Carrera carrera = carrera(idCarreraFinal, carreraActual);
        mapper.actualizar(request, persona);
        normalizar(persona, ci, correo);
        persona.setCarrera(carrera);

        usuario.setTipoUsuario(rol);
        if (request.estado() != null) {
            usuario.setEstado(request.estado());
        }
        if (cambiaRol || desactiva) {
            cerrarSesiones(usuario);
        }
        return mapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse desactivar(Jwt admin, Integer id) {
        Usuario usuario = buscar(id);
        if (esElMismo(admin, usuario)) {
            throw datosInvalidos("No puede desactivar su propia cuenta.");
        }
        usuario.setEstado(Estado.INACTIVO);
        cerrarSesiones(usuario);
        return mapper.toResponse(usuario);
    }

    /**
     * Contrasena temporal: obliga a cambiarla en el proximo login, cierra las sesiones abiertas y avisa al usuario
     * (PASSWORD_RESTABLECIDA; el aviso nunca incluye la contrasena).
     */
    @Transactional
    public void restablecerPassword(Integer id, RestablecerPasswordRequest request) {
        Usuario usuario = buscar(id);
        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuario.setRequiereCambioPassword(Boolean.TRUE);
        cerrarSesiones(usuario);
        notificacionService.notificar(usuario.getIdUsuario(), TipoNotificacion.PASSWORD_RESTABLECIDA,
                TextosNotificacion.TITULO_PASSWORD_RESTABLECIDA, TextosNotificacion.MENSAJE_PASSWORD_RESTABLECIDA,
                TextosNotificacion.ENLACE_SEGURIDAD);
    }

    @Transactional(readOnly = true)
    public List<TipoUsuarioResponse> listarTipos() {
        return tipoUsuarioRepository.findAll(Sort.by("idTipoUsuario")).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // ---------- privados ----------

    private Usuario buscar(Integer id) {
        return usuarioRepository.findDetalleByIdUsuario(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el usuario " + id + "."));
    }

    private TipoUsuario rol(String nombre) {
        return tipoUsuarioRepository.findByNombre(nombre)
                .orElseThrow(() -> datosInvalidos("No existe el rol " + nombre + "."));
    }

    private static void exigirDatosDeEstudiante(TipoUsuario rol, Integer registro, Integer idCarrera) {
        if (TipoUsuario.ESTUDIANTE.equals(rol.getNombre()) && (registro == null || idCarrera == null)) {
            throw datosInvalidos("Para el rol ESTUDIANTE son obligatorios registro e idCarrera.");
        }
    }

    /** 404 si no existe; 409 si esta inactiva y se la asigna de nuevo (conservar la actual esta permitido). */
    private Carrera carrera(Integer idCarrera, Carrera actual) {
        if (idCarrera == null) {
            return null;
        }
        if (actual != null && Objects.equals(actual.getIdCarrera(), idCarrera)) {
            return actual;
        }
        Carrera carrera = carreraRepository.findById(idCarrera)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la carrera " + idCarrera + "."));
        if (!Objects.equals(carrera.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("La carrera " + carrera.getSigla() + " esta inactiva.");
        }
        return carrera;
    }

    private void validarDuplicados(String ci, String correo, Integer registro, Integer idExcluido) {
        if (personaRepository.existsByCiAndIdPersonaNot(ci, idExcluido)) {
            throw NegocioException.conflicto("Ya existe una persona con el CI " + ci + ".");
        }
        if (personaRepository.existsByCorreoIgnoreCaseAndIdPersonaNot(correo, idExcluido)) {
            throw NegocioException.conflicto("Ya existe una persona con el correo " + correo + ".");
        }
        if (registro != null && personaRepository.existsByRegistroAndIdPersonaNot(registro, idExcluido)) {
            throw NegocioException.conflicto("Ya existe una persona con el registro " + registro + ".");
        }
    }

    /** Correos sin espacios y en minusculas: "Ana@UAGRM.edu.bo" y "ana@uagrm.edu.bo" son el mismo. */
    private static String correo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    /** CI sin espacios y en mayusculas ("1234567-1a" = "1234567-1A"); nombres sin espacios a los lados. */
    private static void normalizar(Persona persona, String ci, String correo) {
        persona.setCi(ci);
        persona.setNombre(persona.getNombre().trim());
        persona.setApellidos(persona.getApellidos().trim());
        persona.setCorreo(correo);
    }

    private void cerrarSesiones(Usuario usuario) {
        revocacionTokenService.revocarTodosDesde(usuario.getIdUsuario(), Instant.now());
    }

    private static boolean esElMismo(Jwt admin, Usuario usuario) {
        return admin != null && usuario.getIdUsuario().toString().equals(admin.getSubject());
    }

    private static NegocioException datosInvalidos(String detalle) {
        return new NegocioException(HttpStatus.BAD_REQUEST, "Datos invalidos", detalle);
    }
}
