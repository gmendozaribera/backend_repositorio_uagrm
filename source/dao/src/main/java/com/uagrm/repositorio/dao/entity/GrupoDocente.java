package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Docente asignado a un grupo. esAdminCurso = Docente Administrador de Curso de ese grupo
 * (no es un TipoUsuario propio: un docente puede ser admin de N grupos).
 */
@Entity
@Table(name = "grupo_docente")
@Getter
@Setter
@NoArgsConstructor
public class GrupoDocente {

    @EmbeddedId
    private GrupoDocenteId id = new GrupoDocenteId();

    @MapsId("idGrupo")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo")
    private Grupo grupo;

    @MapsId("idUsuario")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @NotNull
    @Column(nullable = false)
    private Boolean esAdminCurso = Boolean.FALSE;
}
