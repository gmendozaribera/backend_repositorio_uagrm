package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String usuario;

    /** Hash BCrypt, nunca la contrasena en texto plano. */
    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String password;

    /** true mientras el usuario siga con la password_default del padron o la asignada por el admin. */
    @NotNull
    @Column(nullable = false)
    private Boolean requiereCambioPassword = Boolean.TRUE;

    @NotNull
    @Column(nullable = false)
    private Boolean twoFactorEnabled = Boolean.FALSE;

    @Size(max = 64)
    @Column(length = 64)
    private String totpSecret;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_persona", nullable = false, unique = true)
    private Persona persona;

    @NotNull
    @Column(nullable = false)
    private Integer estado = Estado.ACTIVO;
}
