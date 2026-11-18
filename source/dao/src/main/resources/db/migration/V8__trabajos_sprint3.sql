-- Sprint 3 (nucleo de trabajos): trabajo, autores, hashtags, documentos (MinIO o enlace) e historial de estados.
-- Desviacion del modelo (documentada): Autor + DetallePersonaAutor se reemplazan por trabajo_autor
-- (id_trabajo, id_persona). Los autores son siempre personas con cuenta de ESTUDIANTE.

-- ---------------------------------------------------------------- trabajo
-- estado: ciclo de vida (enum EstadoTrabajo + TransicionTrabajo en dao). calificacion llega en el Sprint 4.
-- id_materia e id_semestre se copian del grupo al crear (o al cambiar de grupo en BORRADOR): asi el listado
-- y el repositorio no dependen de que el grupo se edite despues.
CREATE TABLE trabajo (
    id_trabajo          SERIAL        PRIMARY KEY,
    titulo              VARCHAR(200)  NOT NULL,
    resumen             VARCHAR(3000) NOT NULL,
    estado              VARCHAR(30)   NOT NULL DEFAULT 'BORRADOR'
        CONSTRAINT chk_trabajo_estado CHECK (estado IN ('BORRADOR', 'PENDIENTE', 'EN_REVISION', 'APROBADO',
                                                         'APROBADO_CON_OBSERVACIONES', 'RECHAZADO', 'EVALUADO')),
    observaciones       VARCHAR(3000),
    calificacion        INT,
    id_persona_creador  INT           NOT NULL REFERENCES persona (id_persona),
    id_grupo            INT           NOT NULL REFERENCES grupo (id_grupo),
    id_materia          INT           NOT NULL REFERENCES materia (id_materia),
    id_semestre         INT           NOT NULL REFERENCES semestre (id_semestre),
    id_tipo_trabajo     INT           NOT NULL REFERENCES tipo_trabajo (id_tipo_trabajo),
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_envio         TIMESTAMPTZ
);
-- Bandeja de revision del docente: trabajos de sus grupos en ciertos estados.
CREATE INDEX idx_trabajo_grupo_estado ON trabajo (id_grupo, estado);
-- Bandeja de revision del ADMIN (todos los grupos) ordenada por fecha de envio.
CREATE INDEX idx_trabajo_estado_envio ON trabajo (estado, fecha_envio);

-- ---------------------------------------------------------------- trabajo_autor
CREATE TABLE trabajo_autor (
    id_trabajo INT NOT NULL REFERENCES trabajo (id_trabajo) ON DELETE CASCADE,
    id_persona INT NOT NULL REFERENCES persona (id_persona),
    PRIMARY KEY (id_trabajo, id_persona)
);
-- "Mis trabajos": por autor.
CREATE INDEX idx_trabajo_autor_persona ON trabajo_autor (id_persona);

-- ---------------------------------------------------------------- hashtag
-- Texto sin '#' y en minusculas; una fila por hashtag, compartida por todos los trabajos.
CREATE TABLE hashtag (
    id_hashtag SERIAL      PRIMARY KEY,
    texto      VARCHAR(50) NOT NULL,
    CONSTRAINT uq_hashtag_texto UNIQUE (texto)
);

CREATE TABLE trabajo_hashtag (
    id_trabajo INT NOT NULL REFERENCES trabajo (id_trabajo) ON DELETE CASCADE,
    id_hashtag INT NOT NULL REFERENCES hashtag (id_hashtag),
    PRIMARY KEY (id_trabajo, id_hashtag)
);
CREATE INDEX idx_trabajo_hashtag_hashtag ON trabajo_hashtag (id_hashtag);

-- ---------------------------------------------------------------- documento
-- ARCHIVO: guardado en MinIO, ruta_archivo = key del objeto (trabajos/{idTrabajo}/{uuid}.{ext}; nunca el nombre
-- original). ENLACE: url_externo (solo https y hosts permitidos, lo valida la aplicacion).
CREATE TABLE documento (
    id_documento   SERIAL       PRIMARY KEY,
    id_trabajo     INT          NOT NULL REFERENCES trabajo (id_trabajo) ON DELETE CASCADE,
    id_fase        INT          NOT NULL REFERENCES fase (id_fase),
    titulo         VARCHAR(200) NOT NULL,
    descripcion    VARCHAR(500),
    tipo           VARCHAR(10)  NOT NULL CONSTRAINT chk_documento_tipo CHECK (tipo IN ('ARCHIVO', 'ENLACE')),
    categoria      VARCHAR(20)  NOT NULL
        CONSTRAINT chk_documento_categoria CHECK (categoria IN ('DOCUMENTO', 'PRESENTACION', 'CODIGO', 'VIDEO', 'IMAGEN')),
    nombre_archivo VARCHAR(200),
    extension      VARCHAR(10),
    content_type   VARCHAR(150),
    ruta_archivo   VARCHAR(255),
    url_externo    VARCHAR(500),
    tamano_bytes   BIGINT       CONSTRAINT chk_documento_tamano CHECK (tamano_bytes >= 0),
    sha256         CHAR(64),
    fecha_subida   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Un archivo tiene todos sus datos de almacenamiento y ningun enlace; un enlace, al reves.
    CONSTRAINT chk_documento_coherente CHECK (
        (tipo = 'ARCHIVO' AND ruta_archivo IS NOT NULL AND nombre_archivo IS NOT NULL AND extension IS NOT NULL
            AND content_type IS NOT NULL AND tamano_bytes IS NOT NULL AND sha256 IS NOT NULL AND url_externo IS NULL)
        OR
        (tipo = 'ENLACE' AND url_externo IS NOT NULL AND ruta_archivo IS NULL))
);
CREATE INDEX idx_documento_trabajo ON documento (id_trabajo);
CREATE UNIQUE INDEX uq_documento_ruta ON documento (ruta_archivo) WHERE ruta_archivo IS NOT NULL;

-- ---------------------------------------------------------------- trabajo_historial
-- Una fila por cada cambio de estado (estado_anterior NULL = creacion). comentario = observaciones o motivo.
CREATE TABLE trabajo_historial (
    id_historial    SERIAL        PRIMARY KEY,
    id_trabajo      INT           NOT NULL REFERENCES trabajo (id_trabajo) ON DELETE CASCADE,
    estado_anterior VARCHAR(30),
    estado_nuevo    VARCHAR(30)   NOT NULL,
    id_usuario      INT           NOT NULL REFERENCES usuario (id_usuario),
    comentario      VARCHAR(3000),
    fecha           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_trabajo_historial_trabajo ON trabajo_historial (id_trabajo, fecha);

-- ---------------------------------------------------------------- WORK_GROUP
-- Grupos y nombres declarados SOLO en imp ConstantesDominio (regla 11), con el mismo tipo y rango.
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('WORK_GROUP', 'UPLOADS_POR_MINUTO', '10',
     'Subidas de archivos permitidas por usuario en una ventana de 60 segundos; al superarlo responde 429 (entero 1..1000).',
     'sistema', now()),
    ('WORK_GROUP', 'VIDEO_MAX_MB', '100',
     'Tamano maximo en MB de los archivos de la categoria VIDEO (el resto usa el de la facultad) (entero 1..1000).',
     'sistema', now()),
    ('WORK_GROUP', 'MAX_AUTORES', '5',
     'Cantidad maxima de autores de un trabajo, contando al estudiante que lo crea (entero 1..20).',
     'sistema', now());
