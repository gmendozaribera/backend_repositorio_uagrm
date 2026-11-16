-- Sprint 2 (catalogos): Semestre, Materia, Grupo, GrupoDocente, Fase, TipoTrabajo.
-- Facultad y Carrera ya existen desde V1.
-- Desviaciones del modelo (documentadas): semestre tiene estado (para desactivar periodos viejos) y
-- tipo_trabajo.estado es INT como el resto de entidades (el modelo decia VARCHAR).

CREATE TABLE semestre (
    id_semestre SERIAL PRIMARY KEY,
    semestre    INT    NOT NULL CHECK (semestre BETWEEN 1 AND 4),
    anio        INT    NOT NULL CHECK (anio BETWEEN 2000 AND 2100),
    id_facultad INT    NOT NULL REFERENCES facultad (id_facultad),
    estado      INT    NOT NULL DEFAULT 1,
    CONSTRAINT uq_semestre_facultad_periodo UNIQUE (id_facultad, anio, semestre)
);

CREATE TABLE materia (
    id_materia  SERIAL       PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    sigla       VARCHAR(20)  NOT NULL,
    id_facultad INT          NOT NULL REFERENCES facultad (id_facultad),
    estado      INT          NOT NULL DEFAULT 1,
    CONSTRAINT uq_materia_facultad_sigla UNIQUE (id_facultad, sigla)
);

CREATE TABLE grupo (
    id_grupo    SERIAL      PRIMARY KEY,
    nombre      VARCHAR(20) NOT NULL,
    id_materia  INT         NOT NULL REFERENCES materia (id_materia),
    id_semestre INT         NOT NULL REFERENCES semestre (id_semestre),
    estado      INT         NOT NULL DEFAULT 1,
    CONSTRAINT uq_grupo_materia_semestre_nombre UNIQUE (id_materia, id_semestre, nombre)
);
CREATE INDEX idx_grupo_semestre ON grupo (id_semestre);

-- Asignacion docente-grupo. es_admin_curso = Docente Administrador de Curso de ese grupo.
CREATE TABLE grupo_docente (
    id_grupo       INT     NOT NULL REFERENCES grupo (id_grupo),
    id_usuario     INT     NOT NULL REFERENCES usuario (id_usuario),
    es_admin_curso BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_grupo, id_usuario)
);
CREATE INDEX idx_grupo_docente_usuario ON grupo_docente (id_usuario);

CREATE TABLE fase (
    id_fase     SERIAL       PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    id_facultad INT          NOT NULL REFERENCES facultad (id_facultad),
    estado      INT          NOT NULL DEFAULT 1,
    CONSTRAINT uq_fase_facultad_nombre UNIQUE (id_facultad, nombre)
);

CREATE TABLE tipo_trabajo (
    id_tipo_trabajo SERIAL       PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    id_facultad     INT          NOT NULL REFERENCES facultad (id_facultad),
    estado          INT          NOT NULL DEFAULT 1,
    CONSTRAINT uq_tipo_trabajo_facultad_nombre UNIQUE (id_facultad, nombre)
);
