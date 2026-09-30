package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuracion SMTP global del sistema (una sola fila activa). */
@Entity
@Table(name = "config_smtp")
@Getter
@Setter
@NoArgsConstructor
public class ConfigSMTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    @Column(nullable = false)
    private Integer puerto;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String correo;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String password;

    @NotNull
    @Column(nullable = false)
    private Boolean ssl = Boolean.TRUE;

    @NotNull
    @Column(nullable = false)
    private Boolean activo = Boolean.FALSE;
}
