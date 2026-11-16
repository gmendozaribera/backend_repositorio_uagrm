package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dto.padron.ErrorImportacion;
import com.uagrm.repositorio.dto.padron.EstudianteHabilitadoResponse;
import com.uagrm.repositorio.dto.padron.ResultadoImportacion;
import com.uagrm.repositorio.dto.usuario.TipoUsuarioResponse;
import com.uagrm.repositorio.dto.usuario.UsuarioResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Builders del padron estudiantil y de la gestion de usuarios (entidades y responses). */
public final class PadronMock {

    public static final int REGISTRO = 219012345;
    public static final String CI = "7654321";
    public static final String CABECERA = "registro,ci,correo,password_default,nombres,apellidos,sigla_carrera";

    private PadronMock() {
    }

    // ---------- Entidades ----------

    public static EstudianteHabilitado estudianteHabilitado(Carrera carrera, String hashPassword) {
        EstudianteHabilitado estudiante = new EstudianteHabilitado();
        estudiante.setId(70);
        estudiante.setRegistro(REGISTRO);
        estudiante.setCi(CI);
        estudiante.setCorreo("juan.perez@uagrm.edu.bo");
        estudiante.setPasswordDefault(hashPassword);
        estudiante.setNombres("Juan Carlos");
        estudiante.setApellidos("Perez Lopez");
        estudiante.setCarrera(carrera);
        estudiante.setHabilitado(Boolean.TRUE);
        estudiante.setFechaImportacion(Instant.parse("2026-11-16T14:00:00Z"));
        return estudiante;
    }

    // ---------- Responses ----------

    public static EstudianteHabilitadoResponse estudianteHabilitadoResponse() {
        return new EstudianteHabilitadoResponse(70, REGISTRO, CI, "juan.perez@uagrm.edu.bo", "Juan Carlos",
                "Perez Lopez", 10, "INF", true, false);
    }

    public static ResultadoImportacion resultadoImportacion() {
        return new ResultadoImportacion(3, 1, 1, 1,
                List.of(new ErrorImportacion(4, "sigla_carrera", "No existe una carrera activa con la sigla XYZ."),
                        new ErrorImportacion(4, null, "Fila con errores.")));
    }

    public static UsuarioResponse docenteResponse() {
        return new UsuarioResponse(5, "doc.maria", "DOCENTE", Estado.ACTIVO, true, false, 5, "Maria",
                "Lopez Suarez", "5554443", "maria.lopez@uagrm.edu.bo", "70012345", LocalDate.of(1990, 5, 14),
                null, null, null);
    }

    public static UsuarioResponse estudianteResponse() {
        return new UsuarioResponse(1, String.valueOf(REGISTRO), "ESTUDIANTE", Estado.ACTIVO, false, false, 1,
                "Juan Carlos", "Perez Lopez", CI, "juan.perez@uagrm.edu.bo", null, null, REGISTRO, 10, "INF");
    }

    public static List<TipoUsuarioResponse> tiposUsuario() {
        return List.of(
                new TipoUsuarioResponse(1, "ADMIN", "Gestion total del sistema", Estado.ACTIVO),
                new TipoUsuarioResponse(2, "DOCENTE", "Revision y calificacion de trabajos de sus grupos", Estado.ACTIVO),
                new TipoUsuarioResponse(3, "ESTUDIANTE", null, Estado.ACTIVO));
    }
}
