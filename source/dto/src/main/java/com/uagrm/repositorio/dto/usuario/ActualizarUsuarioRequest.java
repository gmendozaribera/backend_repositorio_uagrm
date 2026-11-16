package com.uagrm.repositorio.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PUT de usuario: reemplaza datos personales y rol (celular, fechaNac, registro e idCarrera null se borran).
 * El login no se modifica. estado null conserva el actual.
 */
public record ActualizarUsuarioRequest(
        @NotBlank @Pattern(regexp = ReglasUsuario.PATRON, message = ReglasUsuario.MENSAJE) String rol,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellidos,
        @NotNull @Positive Integer ci,
        @NotBlank @Email @Size(max = 150) String correo,
        @Positive Integer celular,
        @Past LocalDate fechaNac,
        @Positive Integer registro,
        @Positive Integer idCarrera,
        @Min(0) @Max(1) Integer estado) {
}
