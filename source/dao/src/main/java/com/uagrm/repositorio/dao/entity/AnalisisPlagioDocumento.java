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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Foto de un archivo analizado (tomada al iniciar) y su resultado. porcentaje null = aun sin resultado.
 * Solo se guarda el id del documento: si el documento se borra despues, la BD lo deja en null.
 */
@Entity
@Table(name = "analisis_plagio_documento")
@Getter
@Setter
@NoArgsConstructor
public class AnalisisPlagioDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAnalisisDocumento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_analisis", nullable = false)
    private AnalisisPlagio analisis;

    /** 1..documentosAnalizados (sufijo del scanId en Copyleaks). */
    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer indice;

    private Integer idDocumento;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String nombreArchivo;

    /** Key del objeto en MinIO. */
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String rutaArchivo;

    /** SHA-256 del archivo (hexadecimal); CHAR(64) en la BD. */
    @NotNull
    @Size(min = 64, max = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @DecimalMin("0")
    @DecimalMax("100")
    @Column(precision = 5, scale = 2)
    private BigDecimal porcentaje;

    private Instant fechaResultado;

    /** true si el motor ya devolvio el resultado de este documento. */
    public boolean procesado() {
        return porcentaje != null;
    }
}
