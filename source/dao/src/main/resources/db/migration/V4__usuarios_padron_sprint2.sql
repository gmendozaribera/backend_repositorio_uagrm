-- Sprint 2 (usuarios + padron): EstudianteHabilitado = padron estudiantil importado por el administrador.
-- Habilita el auto-registro (POST /api/v1/auth/register): solo CI + registro presentes aqui con habilitado = TRUE.
-- password_default se guarda como hash BCrypt (nunca en texto plano); al registrarse se copia tal cual a usuario.password.

CREATE TABLE estudiante_habilitado (
    id                SERIAL                   PRIMARY KEY,
    registro          INT                      NOT NULL UNIQUE CHECK (registro > 0),
    ci                INT                      NOT NULL UNIQUE CHECK (ci > 0),
    correo            VARCHAR(150)             NOT NULL,
    password_default  VARCHAR(100)             NOT NULL,
    nombres           VARCHAR(100)             NOT NULL,
    apellidos         VARCHAR(100)             NOT NULL,
    id_carrera        INT                      NOT NULL REFERENCES carrera (id_carrera),
    habilitado        BOOLEAN                  NOT NULL DEFAULT TRUE,
    fecha_importacion TIMESTAMP WITH TIME ZONE NOT NULL
);
-- registro y ci ya tienen indice por UNIQUE. Filtro por carrera y orden del listado (apellidos, nombres).
CREATE INDEX idx_estudiante_habilitado_carrera ON estudiante_habilitado (id_carrera);
CREATE INDEX idx_estudiante_habilitado_apellidos ON estudiante_habilitado (apellidos, nombres);

-- Duplicados de correo sin distinguir mayusculas (alta de usuarios y auto-registro)
CREATE INDEX idx_persona_correo_lower ON persona (LOWER(correo));
