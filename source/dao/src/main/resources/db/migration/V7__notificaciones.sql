-- Sprint 2: notificaciones in-app (campana del frontend) y preferencia de recibirlas tambien por correo.
-- La logica vive en imp NotificacionService (la reutiliza work-service en el Sprint 3).

-- ---------------------------------------------------------------- notificacion
-- Cada fila es un aviso para UN usuario. tipo = codigo del evento (enum TipoNotificacion: BIENVENIDA,
-- PASSWORD_RESTABLECIDA...). enlace = ruta interna del frontend (p. ej. /perfil/seguridad) o NULL.
-- Las leidas mas antiguas que NOTIF_GROUP.RETENCION_DIAS las borra el job diario de user-service (Quartz).
CREATE TABLE notificacion (
    id_notificacion SERIAL       PRIMARY KEY,
    id_usuario      INT          NOT NULL REFERENCES usuario (id_usuario),
    tipo            VARCHAR(50)  NOT NULL,
    titulo          VARCHAR(150) NOT NULL,
    mensaje         VARCHAR(500) NOT NULL,
    enlace          VARCHAR(255),
    leida           BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_lectura   TIMESTAMPTZ
);

-- Cubre las consultas de la campana: "mis notificaciones (no leidas) mas recientes primero" y el COUNT de no leidas.
CREATE INDEX idx_notificacion_usuario_leida_fecha
    ON notificacion (id_usuario, leida, fecha_creacion DESC);

-- ---------------------------------------------------------------- usuario.notificar_correo
-- Preferencia del propio usuario (PUT /api/v1/perfil/preferencias): recibir tambien por correo las notificaciones.
-- Solo se envia si ademas el administrador activo el correo saliente (SMTP_GROUP.ACTIVO).
ALTER TABLE usuario ADD COLUMN notificar_correo BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------- NOTIF_GROUP
-- Grupos y nombres declarados SOLO en imp ConstantesDominio (regla 11), con el mismo tipo y rango.
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('NOTIF_GROUP', 'POLLING_SEGUNDOS', '30',
     'Cada cuantos segundos el frontend vuelve a consultar la cantidad de notificaciones sin leer (entero 5..3600).',
     'sistema', now()),
    ('NOTIF_GROUP', 'RETENCION_DIAS', '90',
     'Dias que se conservan las notificaciones YA LEIDAS; el job diario borra las mas antiguas (entero 1..3650).',
     'sistema', now());
