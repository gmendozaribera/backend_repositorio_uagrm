package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notificacion in-app de un usuario (campana del frontend). Se guarda solo el id del usuario (la FK la pone la
 * BD): todas las consultas filtran por el y nunca hace falta cargar la cuenta completa.
 */
@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idNotificacion;

    @NotNull
    @Column(nullable = false)
    private Integer idUsuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoNotificacion tipo;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String titulo;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String mensaje;

    /** Ruta interna del frontend (p. ej. /perfil/seguridad); null si no lleva a ningun lado. */
    @Size(max = 255)
    private String enlace;

    @NotNull
    @Column(nullable = false)
    private Boolean leida = Boolean.FALSE;

    @NotNull
    @Column(nullable = false)
    private Instant fechaCreacion;

    /** null mientras no se lea. */
    private Instant fechaLectura;
}
