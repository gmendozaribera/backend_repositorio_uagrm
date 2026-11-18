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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Documento de un trabajo: un ARCHIVO guardado en MinIO (rutaArchivo = key del objeto, nunca el nombre original)
 * o un ENLACE externo (urlExterno). El CHECK chk_documento_coherente de V8 exige los campos de cada tipo.
 */
@Entity
@Table(name = "documento")
@Getter
@Setter
@NoArgsConstructor
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDocumento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_trabajo", nullable = false)
    private Trabajo trabajo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fase", nullable = false)
    private Fase fase;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String titulo;

    @Size(max = 500)
    @Column(length = 500)
    private String descripcion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoDocumento tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDocumento categoria;

    /** Nombre original saneado (sin rutas); solo para mostrar y para la descarga. */
    @Size(max = 200)
    @Column(length = 200)
    private String nombreArchivo;

    @Size(max = 10)
    @Column(length = 10)
    private String extension;

    /** Content-Type con el que se devuelve en la descarga (el canonico de la extension, ya verificado con Tika). */
    @Size(max = 150)
    @Column(length = 150)
    private String contentType;

    /** Key del objeto en MinIO: trabajos/{idTrabajo}/{uuid}.{ext}. */
    @Size(max = 255)
    @Column(length = 255)
    private String rutaArchivo;

    @Size(max = 500)
    @Column(length = 500)
    private String urlExterno;

    @PositiveOrZero
    private Long tamanoBytes;

    /** SHA-256 en hexadecimal (64 caracteres), calculado al vuelo durante la subida. CHAR(64) en la BD. */
    @Size(min = 64, max = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String sha256;

    @NotNull
    @Column(nullable = false)
    private Instant fechaSubida;
}
