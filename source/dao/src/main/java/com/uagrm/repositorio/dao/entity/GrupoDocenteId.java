package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Clave compuesta de GrupoDocente (id_grupo + id_usuario). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GrupoDocenteId implements Serializable {

    @Column(name = "id_grupo")
    private Integer idGrupo;

    @Column(name = "id_usuario")
    private Integer idUsuario;
}
