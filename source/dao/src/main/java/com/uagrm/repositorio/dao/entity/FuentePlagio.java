package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fuente con la que coincide un analisis. titulo es el dato tal como lo entrego el motor: al responder, una fuente
 * INTERNA cuyo trabajo no esta EVALUADO se muestra con un titulo generico y sin id (ver PlagioMapper).
 */
@Entity
@Table(name = "fuente_plagio")
@Getter
@Setter
@NoArgsConstructor
public class FuentePlagio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idFuente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_analisis", nullable = false)
    private AnalisisPlagio analisis;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoFuentePlagio tipo;

    @NotBlank
    @Size(max = 300)
    @Column(nullable = false, length = 300)
    private String titulo;

    @Size(max = 1000)
    @Column(length = 1000)
    private String url;

    /** Trabajo interno que coincide (null si es de internet o no se pudo identificar). */
    private Integer idTrabajoFuente;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;
}
