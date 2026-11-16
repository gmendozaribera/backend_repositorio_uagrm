package com.uagrm.repositorio.dto.usuario;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PUT de usuario: reemplaza datos personales y rol (celular y fechaNac null se borran).
 * registro, idCarrera y estado null conservan el valor actual (un ESTUDIANTE nunca queda sin carrera ni registro).
 * El login no se modifica.
 */
public record ActualizarUsuarioRequest(
        @NotBlank @Pattern(regexp = ReglasUsuario.PATRON, message = ReglasUsuario.MENSAJE) String rol,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank
        @Size(min = 4, max = CarnetIdentidad.LONGITUD_MAXIMA)
        @Pattern(regexp = CarnetIdentidad.PATRON, message = CarnetIdentidad.MENSAJE)
        String ci,
        @NotBlank @Email @Size(max = 150) String correo,
        @Size(max = 20) @Pattern(regexp = CarnetIdentidad.PATRON_CELULAR, message = CarnetIdentidad.MENSAJE_CELULAR)
        String celular,
        @Past LocalDate fechaNac,
        @Positive Integer registro,
        @Positive Integer idCarrera,
        @Min(0) @Max(1) Integer estado) {
}
