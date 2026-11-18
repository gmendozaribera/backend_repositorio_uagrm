package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Un analisis de plagio de los documentos de un trabajo (V11; reemplaza a ResultadoPlagio del modelo).
 * Un trabajo puede tener varios (historial), pero solo uno EN_PROCESO a la vez (indice unico parcial).
 * Documentos y fuentes usan @BatchSize: el historial se carga en pocas consultas, sin N+1.
 */
@Entity
@Table(name = "analisis_plagio")
@Getter
@Setter
@NoArgsConstructor
public class AnalisisPlagio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAnalisis;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajo", nullable = false)
    private Trabajo trabajo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoAnalisisPlagio estado = EstadoAnalisisPlagio.EN_PROCESO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoMotorPlagio motor;

    /** Solo en COMPLETADO: el maximo de los porcentajes de sus documentos. */
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(precision = 5, scale = 2)
    private BigDecimal porcentaje;

    /** Solo en COMPLETADO. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private NivelPlagio nivel;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private Integer umbralMedio;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private Integer umbralAlto;

    @NotNull
    @Column(nullable = false)
    private Boolean comparacionInternet;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer documentosAnalizados;

    /** Solo en ERROR: motivo legible, sin datos internos. */
    @Size(max = 500)
    @Column(length = 500)
    private String mensajeError;

    /** Prefijo de los scans del proveedor externo (cada documento: {scanId}-{indice}); null en SIMULADO. */
    @Size(max = 36)
    @Column(length = 36, unique = true)
    private String scanId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_inicio", nullable = false)
    private Usuario usuarioInicio;

    @NotNull
    @Column(nullable = false)
    private Instant fechaInicio;

    private Instant fechaFin;

    @OneToMany(mappedBy = "analisis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("indice ASC")
    @BatchSize(size = 50)
    private List<AnalisisPlagioDocumento> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "analisis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("porcentaje DESC, idFuente ASC")
    @BatchSize(size = 50)
    private List<FuentePlagio> fuentes = new ArrayList<>();

    /** true si ya esta COMPLETADO o ERROR (estados finales). */
    public boolean terminado() {
        return estado != EstadoAnalisisPlagio.EN_PROCESO;
    }
}
