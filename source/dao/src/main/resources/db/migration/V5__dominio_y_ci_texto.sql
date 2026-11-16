-- Sprint 2: tabla de dominio (parametros configurables del sistema, regla 11 de CLAUDE.md) y CI/celular como texto.

-- ---------------------------------------------------------------- Dominio
-- Cada parametro se identifica por grupo + nombre. Los que usa el codigo se declaran SOLO en
-- imp ConstantesDominio (con su tipo, rango y valor por defecto de respaldo) y se leen con cache Redis
-- (config:dominio:{GRUPO}, TTL 600 s). Se administran con /api/v1/dominios (user-service).
CREATE TABLE dominio (
    domain_id    SERIAL                   PRIMARY KEY,
    domain_group VARCHAR(50)              NOT NULL,
    domain_name  VARCHAR(50)              NOT NULL,
    domain_value VARCHAR(500)             NOT NULL,
    description  VARCHAR(255)             NOT NULL,
    last_user    VARCHAR(50)              NOT NULL,
    last_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_dominio_grupo_nombre UNIQUE (domain_group, domain_name)
);

INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('AUTH_GROUP', 'LOGIN_INTENTOS', '10',
     'Intentos fallidos permitidos por IP en el login y en el auto-registro antes de bloquear esa IP (entero 1..1000).',
     'sistema', now()),
    ('AUTH_GROUP', 'LOGIN_WAITING', '15',
     'Minutos que una IP queda bloqueada para login y auto-registro tras superar LOGIN_INTENTOS (entero 1..1440).',
     'sistema', now());

-- ---------------------------------------------------------------- CI y celular como texto
-- El CI puede llevar complemento (1234567-1A); el celular puede llevar prefijo internacional (+591...).
-- La restriccion UNIQUE se conserva al cambiar el tipo (el indice se reconstruye).
ALTER TABLE persona
    ALTER COLUMN ci TYPE VARCHAR(20) USING ci::text,
    ALTER COLUMN celular TYPE VARCHAR(20) USING celular::text;

-- El CHECK (ci > 0) de V4 compara con un entero: se elimina antes de cambiar el tipo
-- (el formato del CI lo valida la aplicacion con el patron del contrato).
ALTER TABLE estudiante_habilitado DROP CONSTRAINT IF EXISTS estudiante_habilitado_ci_check;
ALTER TABLE estudiante_habilitado
    ALTER COLUMN ci TYPE VARCHAR(20) USING ci::text;
