package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "config_facultad")
@Getter
@Setter
@NoArgsConstructor
public class ConfigFacultad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_facultad", nullable = false, unique = true)
    private Facultad facultad;

    @NotNull
    @Min(1)
    @Max(100)
    @Column(name = "tamano_max_archivo_mb", nullable = false)
    private Integer tamanoMaxArchivoMB = 10;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private Integer umbralPlagioMedio = 30;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private Integer umbralPlagioAlto = 50;

    @NotNull
    @Column(nullable = false)
    private Boolean comparacionInternet = Boolean.FALSE;

    @NotNull
    @Column(nullable = false)
    private Boolean accesoExterno = Boolean.FALSE;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer escalaCalifMax = 100;

    @NotNull
    @Min(1)
    @Max(24)
    @Column(nullable = false)
    private Integer jwtExpiracionHoras = 1;
}
