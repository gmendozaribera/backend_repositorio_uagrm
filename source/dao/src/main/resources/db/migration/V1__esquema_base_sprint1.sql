-- Sprint 1: Facultad, Carrera, Persona, TipoUsuario, Usuario, ConfigFacultad, ConfigSMTP

CREATE TABLE facultad (
    id_facultad SERIAL       PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    sigla       VARCHAR(20)  NOT NULL UNIQUE,
    estado      INT          NOT NULL DEFAULT 1
);

CREATE TABLE carrera (
    id_carrera  SERIAL       PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    sigla       VARCHAR(20)  NOT NULL UNIQUE,
    id_facultad INT          NOT NULL REFERENCES facultad (id_facultad),
    estado      INT          NOT NULL DEFAULT 1
);
CREATE INDEX idx_carrera_facultad ON carrera (id_facultad);

CREATE TABLE persona (
    id_persona SERIAL       PRIMARY KEY,
    nombre     VARCHAR(100) NOT NULL,
    apellidos  VARCHAR(100) NOT NULL,
    ci         INT          NOT NULL UNIQUE,
    correo     VARCHAR(150) NOT NULL UNIQUE,
    celular    INT,
    fecha_nac  DATE,
    registro   INT          UNIQUE,
    id_carrera INT          REFERENCES carrera (id_carrera)
);
CREATE INDEX idx_persona_carrera ON persona (id_carrera);

CREATE TABLE tipo_usuario (
    id_tipo_usuario SERIAL       PRIMARY KEY,
    nombre          VARCHAR(50)  NOT NULL UNIQUE,
    descripcion     VARCHAR(255),
    estado          INT          NOT NULL DEFAULT 1
);

CREATE TABLE usuario (
    id_usuario               SERIAL       PRIMARY KEY,
    usuario                  VARCHAR(50)  NOT NULL UNIQUE,
    password                 VARCHAR(100) NOT NULL,
    requiere_cambio_password BOOLEAN      NOT NULL DEFAULT TRUE,
    two_factor_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_secret              VARCHAR(64),
    id_tipo_usuario          INT          NOT NULL REFERENCES tipo_usuario (id_tipo_usuario),
    id_persona               INT          NOT NULL UNIQUE REFERENCES persona (id_persona),
    estado                   INT          NOT NULL DEFAULT 1
);
CREATE INDEX idx_usuario_tipo ON usuario (id_tipo_usuario);

CREATE TABLE config_facultad (
    id                    SERIAL  PRIMARY KEY,
    id_facultad           INT     NOT NULL UNIQUE REFERENCES facultad (id_facultad),
    tamano_max_archivo_mb INT     NOT NULL DEFAULT 10  CHECK (tamano_max_archivo_mb BETWEEN 1 AND 100),
    umbral_plagio_medio   INT     NOT NULL DEFAULT 30  CHECK (umbral_plagio_medio BETWEEN 0 AND 100),
    umbral_plagio_alto    INT     NOT NULL DEFAULT 50  CHECK (umbral_plagio_alto BETWEEN 0 AND 100),
    comparacion_internet  BOOLEAN NOT NULL DEFAULT FALSE,
    acceso_externo        BOOLEAN NOT NULL DEFAULT FALSE,
    escala_calif_max      INT     NOT NULL DEFAULT 100 CHECK (escala_calif_max >= 1),
    jwt_expiracion_horas  INT     NOT NULL DEFAULT 1   CHECK (jwt_expiracion_horas BETWEEN 1 AND 24),
    CONSTRAINT chk_umbrales_plagio CHECK (umbral_plagio_medio < umbral_plagio_alto)
);

CREATE TABLE config_smtp (
    id       SERIAL       PRIMARY KEY,
    host     VARCHAR(150) NOT NULL,
    puerto   INT          NOT NULL CHECK (puerto BETWEEN 1 AND 65535),
    correo   VARCHAR(150) NOT NULL,
    password VARCHAR(255) NOT NULL,
    ssl      BOOLEAN      NOT NULL DEFAULT TRUE,
    activo   BOOLEAN      NOT NULL DEFAULT FALSE
);
-- Solo puede existir una configuracion SMTP activa a la vez
CREATE UNIQUE INDEX uq_config_smtp_activo ON config_smtp (activo) WHERE activo;
