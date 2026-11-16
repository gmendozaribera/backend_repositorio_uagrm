package com.uagrm.repositorio.dto.usuario;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Alta de persona + cuenta por el ADMIN. registro e idCarrera son obligatorios para ESTUDIANTE (lo valida el service). */
public record CrearUsuarioRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "solo letras, numeros, punto, guion y guion bajo")
        String usuario,
        @NotBlank
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = ReglasUsuario.PATRON_PASSWORD, message = ReglasUsuario.MENSAJE_PASSWORD)
        String passwordInicial,
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
        @Positive Integer idCarrera) {
}
