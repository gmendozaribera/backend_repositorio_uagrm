package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.TransicionTrabajo;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.UsuarioAutenticado;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Quien ve y quien hace que con un trabajo (contrato work-api.yaml, "Quien ve y hace que").
 *
 * <p>En terminos simples: un trabajo solo lo ven sus autores, los docentes asignados a su grupo y el ADMIN. Para
 * cualquier otro usuario el trabajo "no existe" (404, el mismo mensaje que un id inexistente): asi nadie puede
 * descubrir que trabajos hay probando numeros. Quien SI lo ve pero no puede hacer una accion (un docente que
 * intenta editar) recibe 403; si la accion no corresponde al estado actual, 409.</p>
 *
 * <p>Excepcion: un trabajo EVALUADO esta publicado. Su ficha publica la sirve {@link RepositorioService} y sus
 * archivos los puede descargar cualquier usuario autenticado ({@link #puedeDescargar}).</p>
 */
@Component
@RequiredArgsConstructor
public class AccesoTrabajos {

    private final UsuarioRepository usuarioRepository;
    private final TrabajoRepository trabajoRepository;
    private final GrupoDocenteRepository grupoDocenteRepository;

    /**
     * Relacion del usuario con un trabajo concreto.
     *
     * @param adminCurso docente del grupo marcado como Administrador de Curso (grupo_docente.es_admin_curso)
     */
    public record Permisos(boolean autor, boolean docenteDelGrupo, boolean adminCurso, boolean admin) {

        public boolean visible() {
            return autor || docenteDelGrupo || admin;
        }
    }

    /** Usuario del token con rol y persona. 401 si la cuenta ya no existe. */
    public ActorTrabajo actor(Jwt jwt) {
        return usuarioRepository.findConRolByIdUsuario(UsuarioAutenticado.id(jwt))
                .map(ActorTrabajo::new)
                .orElseThrow(() -> NegocioException.noAutorizado("La sesion ya no es valida."));
    }

    /** Trabajo visible para el actor (con sus catalogos); 404 si no existe o no puede verlo. */
    public Trabajo paraLeer(Integer idTrabajo, ActorTrabajo actor) {
        Trabajo trabajo = trabajoRepository.findDetalleByIdTrabajo(idTrabajo).orElseThrow(() -> noExiste(idTrabajo));
        exigirVisible(trabajo, actor);
        return trabajo;
    }

    /**
     * Trabajo visible para el actor, bloqueado (SELECT ... FOR UPDATE) hasta el fin de la transaccion: dos cambios
     * simultaneos sobre el mismo trabajo no pueden pisarse. 404 si no existe o no puede verlo.
     */
    public Trabajo paraModificar(Integer idTrabajo, ActorTrabajo actor) {
        Trabajo trabajo = trabajoRepository.findParaActualizar(idTrabajo).orElseThrow(() -> noExiste(idTrabajo));
        exigirVisible(trabajo, actor);
        return trabajo;
    }

    public Permisos permisos(Trabajo trabajo, ActorTrabajo actor) {
        boolean autor = actor.esEstudiante() && trabajo.getAutores().stream()
                .anyMatch(p -> Objects.equals(p.getIdPersona(), actor.idPersona()));
        Optional<Boolean> asignacion = actor.esDocente()
                ? grupoDocenteRepository.findEsAdminCurso(trabajo.getGrupo().getIdGrupo(), actor.idUsuario())
                : Optional.empty();
        return new Permisos(autor, asignacion.isPresent(), asignacion.orElse(false), actor.esAdmin());
    }

    /**
     * Descargar un archivo: un trabajo EVALUADO esta publicado en el repositorio y cualquier usuario autenticado
     * puede descargar sus archivos; en cualquier otro estado, solo quien puede ver el trabajo. Ver el detalle
     * interno (/trabajos/{id}) NO se abre: sigue {@link #paraLeer}.
     */
    public boolean puedeDescargar(Trabajo trabajo, ActorTrabajo actor) {
        return trabajo.getEstado() == EstadoTrabajo.EVALUADO || permisos(trabajo, actor).visible();
    }

    /** 403 si no es autor; 409 si el estado no permite editar. */
    public void exigirAutorEditable(Trabajo trabajo, ActorTrabajo actor) {
        exigirAutor(trabajo, actor);
        if (!trabajo.getEstado().editable()) {
            throw NegocioException.conflicto("El trabajo esta en estado " + trabajo.getEstado()
                    + " y no se puede modificar.");
        }
    }

    public void exigirAutor(Trabajo trabajo, ActorTrabajo actor) {
        if (!permisos(trabajo, actor).autor()) {
            throw NegocioException.accesoDenegado("Solo los autores del trabajo pueden realizar esta accion.");
        }
    }

    public void exigirDocenteDelGrupo(Trabajo trabajo, ActorTrabajo actor) {
        if (!permisos(trabajo, actor).docenteDelGrupo()) {
            throw NegocioException.accesoDenegado("Solo los docentes asignados al grupo pueden revisar el trabajo.");
        }
    }

    /**
     * Calificar: solo el Administrador de Curso del grupo. Un docente del grupo sin ese rol (u otro rol que si ve
     * el trabajo) recibe 403; quien no ve el trabajo ya recibio 404 en {@link #paraModificar}.
     */
    public void exigirAdminCurso(Trabajo trabajo, ActorTrabajo actor) {
        if (!permisos(trabajo, actor).adminCurso()) {
            throw NegocioException.accesoDenegado(
                    "Solo el Administrador de Curso del grupo puede calificar el trabajo.");
        }
    }

    /** Acciones disponibles ahora para el actor, segun su relacion con el trabajo y la maquina de estados. */
    public AccionesResponse acciones(Trabajo trabajo, Permisos permisos) {
        EstadoTrabajo estado = trabajo.getEstado();
        boolean autor = permisos.autor();
        boolean docente = permisos.docenteDelGrupo();
        return new AccionesResponse(
                autor && estado.editable(),
                autor && TransicionTrabajo.ENVIAR.aplicableDesde(estado),
                autor && estado == EstadoTrabajo.BORRADOR,
                docente && TransicionTrabajo.TOMAR_REVISION.aplicableDesde(estado),
                docente && TransicionTrabajo.APROBAR.aplicableDesde(estado),
                docente && TransicionTrabajo.RECHAZAR.aplicableDesde(estado),
                permisos.adminCurso() && TransicionTrabajo.CALIFICAR.aplicableDesde(estado));
    }

    private void exigirVisible(Trabajo trabajo, ActorTrabajo actor) {
        if (!permisos(trabajo, actor).visible()) {
            throw noExiste(trabajo.getIdTrabajo());
        }
    }

    public static NegocioException noExiste(Integer idTrabajo) {
        return NegocioException.noEncontrado("No existe el trabajo " + idTrabajo + ".");
    }
}
