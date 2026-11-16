package com.uagrm.repositorio.dto.dominio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/config/smtp/prueba: a quien enviar el correo de prueba. */
public record PruebaSmtpRequest(@NotBlank @Email @Size(max = 150) String destinatario) {
}
