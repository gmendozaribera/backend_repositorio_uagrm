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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Trabajo academico. Materia y semestre se copian del grupo. Los documentos y el historial se consultan con sus
 * repositorios (no se mapean como colecciones aqui para no cargarlos en los listados).
 * Autores y hashtags usan @BatchSize: en un listado paginado se cargan en pocas consultas, sin N+1.
 */
@Entity
@Table(name = "trabajo")
@Getter
@Setter
@NoArgsConstructor
public class Trabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTrabajo;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String titulo;

    @NotBlank
    @Size(max = 3000)
    @Column(nullable = false, length = 3000)
    private String resumen;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTrabajo estado = EstadoTrabajo.BORRADOR;

    /** Ultimas observaciones (aprobacion) o motivo de rechazo del docente. */
    @Size(max = 3000)
    @Column(length = 3000)
    private String observaciones;

    /** Sprint 4. */
    private Integer calificacion;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_persona_creador", nullable = false)
    private Persona creador;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_semestre", nullable = false)
    private Semestre semestre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_trabajo", nullable = false)
    private TipoTrabajo tipoTrabajo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "trabajo_autor",
            joinColumns = @JoinColumn(name = "id_trabajo"),
            inverseJoinColumns = @JoinColumn(name = "id_persona"))
    @BatchSize(size = 50)
    private Set<Persona> autores = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "trabajo_hashtag",
            joinColumns = @JoinColumn(name = "id_trabajo"),
            inverseJoinColumns = @JoinColumn(name = "id_hashtag"))
    @BatchSize(size = 50)
    private Set<Hashtag> hashtags = new LinkedHashSet<>();

    @NotNull
    @Column(nullable = false)
    private Instant fechaCreacion;

    @NotNull
    @Column(nullable = false)
    private Instant fechaActualizacion;

    /** Ultima vez que se envio a revision; null mientras sea un borrador nunca enviado. */
    private Instant fechaEnvio;
}
