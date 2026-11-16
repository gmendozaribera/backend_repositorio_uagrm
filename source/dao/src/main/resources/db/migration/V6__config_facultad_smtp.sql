-- Sprint 2: valores por defecto de la configuracion por facultad y configuracion SMTP en la tabla de dominio.
-- Los grupos y nombres se declaran SOLO en imp ConstantesDominio (regla 11), con el mismo tipo y rango.

-- ---------------------------------------------------------------- FACULTAD_DEFAULT_GROUP
-- Se usan cuando una facultad aun no guardo su propia fila en config_facultad (GET /facultades/{id}/config
-- responde con personalizada = false). Rangos iguales a los CHECK de config_facultad (V1) y al contrato.
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('FACULTAD_DEFAULT_GROUP', 'TAMANO_MAX_ARCHIVO_MB', '10',
     'Valor por defecto para facultades sin configuracion propia: tamano maximo por archivo en MB (entero 1..100).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'UMBRAL_PLAGIO_MEDIO', '30',
     'Valor por defecto para facultades sin configuracion propia: % de similitud desde el que el riesgo es medio (entero 0..100, menor que UMBRAL_PLAGIO_ALTO).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'UMBRAL_PLAGIO_ALTO', '50',
     'Valor por defecto para facultades sin configuracion propia: % de similitud desde el que el riesgo es alto (entero 0..100, mayor que UMBRAL_PLAGIO_MEDIO).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'COMPARACION_INTERNET', 'false',
     'Valor por defecto para facultades sin configuracion propia: el analisis de plagio compara tambien con internet (true/false).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'ACCESO_EXTERNO', 'false',
     'Valor por defecto para facultades sin configuracion propia: usuarios anonimos ven los metadatos de trabajos evaluados (true/false).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'ESCALA_CALIF_MAX', '100',
     'Valor por defecto para facultades sin configuracion propia: calificacion maxima, la escala va de 0 a este valor (entero 1..1000).',
     'sistema', now()),
    ('FACULTAD_DEFAULT_GROUP', 'JWT_EXPIRACION_HORAS', '1',
     'Valor por defecto para facultades sin configuracion propia y usuarios sin carrera: horas de vida del access token (entero 1..24).',
     'sistema', now());

-- ---------------------------------------------------------------- SMTP_GROUP
-- Correo saliente del sistema. PASSWORD es SECRETO: se guarda cifrado con AES-GCM ('v1:...', llave en la
-- variable de entorno DOMINIO_CIFRADO_KEY, nunca en la BD) y la API no lo devuelve. Vacio = sin configurar.
-- HOST vacio = correo aun sin configurar (la prueba SMTP responde 400 hasta que se cargue).
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('SMTP_GROUP', 'HOST', '',
     'Servidor SMTP de correo saliente (p. ej. smtp.gmail.com). Vacio = correo sin configurar.',
     'sistema', now()),
    ('SMTP_GROUP', 'PUERTO', '587',
     'Puerto del servidor SMTP (entero 1..65535; normalmente 587 con STARTTLS o 465 con SSL).',
     'sistema', now()),
    ('SMTP_GROUP', 'CORREO', 'no-responder@uagrm.edu.bo',
     'Cuenta remitente y usuario de autenticacion SMTP (correo valido).',
     'sistema', now()),
    ('SMTP_GROUP', 'PASSWORD', '',
     'Contrasena de la cuenta SMTP. Secreto: se guarda cifrado y nunca se muestra. Vacio = sin configurar.',
     'sistema', now()),
    ('SMTP_GROUP', 'SEGURIDAD', 'STARTTLS',
     'Cifrado de la conexion SMTP: SSL, STARTTLS o NINGUNA.',
     'sistema', now()),
    ('SMTP_GROUP', 'ACTIVO', 'false',
     'Si el sistema envia notificaciones por correo (true/false). La prueba SMTP funciona aunque sea false.',
     'sistema', now());

-- ---------------------------------------------------------------- config_smtp (se elimina)
-- Por decision del usuario la configuracion SMTP vive ahora en la tabla de dominio (grupo SMTP_GROUP):
-- se administra con el mismo CRUD /api/v1/dominios, con cache Redis y con la contrasena cifrada.
-- La tabla config_smtp de V1 nunca se uso (no tenia endpoints ni datos), asi que se elimina junto con su
-- indice parcial; tambien se borraron la entidad ConfigSMTP y su repositorio.
DROP TABLE config_smtp;
