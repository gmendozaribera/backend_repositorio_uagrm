package com.uagrm.repositorio.imp.service;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.EstudianteHabilitadoRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.repository.TipoUsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.auth.RegistroRequest;
import com.uagrm.repositorio.dto.auth.RegistroResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.LimiteIntentosService;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.notificacion.TextosNotificacion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-registro de estudiantes (CU-REG). Solo quien figura habilitado en el padron puede crear su cuenta:
 * usuario = numero de registro y contrasena = la password_default entregada por su facultad, que el padron ya
 * guarda como hash BCrypt y se copia tal cual (no se vuelve a cifrar). El primer login obliga a cambiarla.
 * La cuenta nueva recibe la notificacion BIENVENIDA.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    static final String NO_EN_PADRON = "No encontrado en el padron institucional. Contacte a su facultad.";
    static final String CUENTA_EXISTENTE =
            "Ya tiene una cuenta registrada. Si olvido su contrasena, contacte a su facultad.";

    private final EstudianteHabilitadoRepository padronRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final LimiteIntentosService limiteIntentos;
    private final NotificacionService notificacionService;

    /**
     * Limite de intentos por IP: si la IP ya agoto sus intentos responde 429; cada 404 (no esta en el padron) o
     * 409 (cuenta existente) suma uno. Asi nadie puede recorrer el padron probando combinaciones de CI y registro.
     */
    @Transactional
    public RegistroResponse registrar(RegistroRequest request, String ip) {
        limiteIntentos.verificar(LimiteIntentosService.Operacion.REGISTRO, ip);
        try {
            return crearCuenta(request);
        } catch (NegocioException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND || ex.getStatus() == HttpStatus.CONFLICT) {
                limiteIntentos.registrarFallo(LimiteIntentosService.Operacion.REGISTRO, ip);
            }
            throw ex;
        }
    }

    private RegistroResponse crearCuenta(RegistroRequest request) {
        String ci = CarnetIdentidad.normalizar(request.ci());
        EstudianteHabilitado padron = padronRepository.findByCiAndRegistro(ci, request.registro())
                .filter(fila -> Boolean.TRUE.equals(fila.getHabilitado()))
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "No encontrado en el padron", NO_EN_PADRON));

        String login = padron.getRegistro().toString();
        if (personaRepository.existsByCi(padron.getCi())
                || personaRepository.existsByRegistro(padron.getRegistro())
                || usuarioRepository.existsByUsuario(login)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Cuenta existente", CUENTA_EXISTENTE);
        }
        if (personaRepository.existsByCorreoIgnoreCase(padron.getCorreo())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Cuenta existente",
                    "Su correo ya pertenece a otra cuenta. Contacte a su facultad.");
        }
        TipoUsuario estudiante = tipoUsuarioRepository.findByNombre(TipoUsuario.ESTUDIANTE)
                .orElseThrow(() -> new IllegalStateException("Falta el tipo de usuario ESTUDIANTE (migracion V2)"));

        Persona persona = new Persona();
        persona.setNombre(padron.getNombres());
        persona.setApellidos(padron.getApellidos());
        persona.setCi(padron.getCi());
        persona.setCorreo(padron.getCorreo());
        persona.setRegistro(padron.getRegistro());
        persona.setCarrera(padron.getCarrera());
        personaRepository.save(persona);

        Usuario usuario = new Usuario();
        usuario.setUsuario(login);
        usuario.setPassword(padron.getPasswordDefault());
        usuario.setRequiereCambioPassword(Boolean.TRUE);
        usuario.setTwoFactorEnabled(Boolean.FALSE);
        usuario.setTipoUsuario(estudiante);
        usuario.setPersona(persona);
        usuario.setEstado(Estado.ACTIVO);
        usuarioRepository.save(usuario);

        // Misma transaccion: si algo falla despues, la cuenta y su bienvenida se revierten juntas
        notificacionService.notificar(usuario.getIdUsuario(), TipoNotificacion.BIENVENIDA,
                TextosNotificacion.TITULO_BIENVENIDA, TextosNotificacion.MENSAJE_BIENVENIDA_REGISTRO,
                TextosNotificacion.ENLACE_SEGURIDAD);
        return new RegistroResponse(login);
    }
}
