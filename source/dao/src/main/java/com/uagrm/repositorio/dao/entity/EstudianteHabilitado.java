package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila del padron estudiantil importado por el administrador (CSV o Excel).
 * Un estudiante solo puede auto-registrarse si su CI + registro estan aqui con habilitado = true.
 */
@Entity
@Table(name = "estudiante_habilitado")
@Getter
@Setter
@NoArgsConstructor
public class EstudianteHabilitado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @Positive
    @Column(nullable = false, unique = true)
    private Integer registro;

    /** Texto con complemento opcional (1234567-1A); sin espacios y en mayusculas (ver CarnetIdentidad). */
    @NotBlank
    @Size(max = CarnetIdentidad.LONGITUD_MAXIMA)
    @Column(nullable = false, unique = true, length = CarnetIdentidad.LONGITUD_MAXIMA)
    private String ci;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String correo;

    /** Hash BCrypt de la contrasena por defecto entregada por la facultad. Se copia tal cual al registrarse. */
    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String passwordDefault;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nombres;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String apellidos;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_carrera", nullable = false)
    private Carrera carrera;

    /** false = no puede auto-registrarse. No afecta a una cuenta ya creada. */
    @NotNull
    @Column(nullable = false)
    private Boolean habilitado = Boolean.TRUE;

    @NotNull
    @Column(nullable = false)
    private Instant fechaImportacion;
}
