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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Periodo academico de una facultad: numero de periodo (1..4) + anio. Unico por facultad.
 * Tiene estado (no estaba en el modelo) para poder desactivar periodos viejos.
 */
@Entity
@Table(name = "semestre")
@Getter
@Setter
@NoArgsConstructor
public class Semestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSemestre;

    @NotNull
    @Min(1)
    @Max(4)
    @Column(nullable = false)
    private Integer semestre;

    @NotNull
    @Min(2000)
    @Max(2100)
    @Column(nullable = false)
    private Integer anio;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_facultad", nullable = false)
    private Facultad facultad;

    @NotNull
    @Column(nullable = false)
    private Integer estado = Estado.ACTIVO;
}
