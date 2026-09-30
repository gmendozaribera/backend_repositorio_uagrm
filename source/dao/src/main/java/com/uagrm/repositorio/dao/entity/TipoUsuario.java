package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol del usuario: ADMIN, DOCENTE o ESTUDIANTE.
 * DOCENTE_ADMIN_CURSO no es un tipo propio: se deriva del flag esAdminCurso en GrupoDocente.
 */
@Entity
@Table(name = "tipo_usuario")
@Getter
@Setter
@NoArgsConstructor
public class TipoUsuario {

    public static final String ADMIN = "ADMIN";
    public static final String DOCENTE = "DOCENTE";
    public static final String ESTUDIANTE = "ESTUDIANTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTipoUsuario;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Size(max = 255)
    private String descripcion;

    @NotNull
    @Column(nullable = false)
    private Integer estado = Estado.ACTIVO;
}
