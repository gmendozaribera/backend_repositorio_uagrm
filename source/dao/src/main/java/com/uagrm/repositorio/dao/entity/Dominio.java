package com.uagrm.repositorio.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parametro configurable del sistema (tabla de dominio, regla 11 de CLAUDE.md).
 * grupo + nombre es unico. Los parametros que usa el codigo se declaran en imp ConstantesDominio.
 */
@Entity
@Table(name = "dominio", uniqueConstraints = @UniqueConstraint(
        name = "uk_dominio_grupo_nombre", columnNames = {"domain_group", "domain_name"}))
@Getter
@Setter
@NoArgsConstructor
public class Dominio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer domainId;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String domainGroup;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String domainName;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String domainValue;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String description;

    /** Login del ultimo usuario que lo modifico; 'sistema' si vino de una migracion. */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String lastUser;

    @NotNull
    @Column(nullable = false)
    private Instant lastTime;
}
