-- Sprint 4 (CU14): deteccion de plagio asincrona (plagiarism-service :8085, contrato plagio-api.yaml).
--
-- Desviacion del modelo (documentada): la entidad ResultadoPlagio del modelo se reemplaza por:
--   analisis_plagio            un analisis por cada vez que un docente lo inicia (historial completo, estado
--                              EN_PROCESO -> COMPLETADO | ERROR, motor usado, umbrales con los que se calculo el nivel)
--   analisis_plagio_documento  foto de los archivos analizados (pdf/docx/odt) y el porcentaje de cada uno
--                              (el porcentaje del trabajo es el MAXIMO de sus documentos)
--   fuente_plagio              fuentes encontradas (INTERNA = otro trabajo del repositorio, INTERNET = una url)
-- La logica vive en imp PlagioService; los motores (SIMULADO / COPYLEAKS) en plagiarism-service.

-- ---------------------------------------------------------------- analisis_plagio
-- porcentaje y nivel solo en COMPLETADO; mensaje_error solo en ERROR. umbral_medio/umbral_alto: los de
-- ConfigFacultad vigentes al terminar (al iniciar se guardan los de ese momento).
-- scan_id: prefijo de los scans del proveedor externo (Copyleaks); cada documento usa {scan_id}-{indice}.
CREATE TABLE analisis_plagio (
    id_analisis           SERIAL       PRIMARY KEY,
    id_trabajo            INT          NOT NULL REFERENCES trabajo (id_trabajo) ON DELETE CASCADE,
    estado                VARCHAR(15)  NOT NULL
        CONSTRAINT chk_analisis_plagio_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADO', 'ERROR')),
    motor                 VARCHAR(15)  NOT NULL
        CONSTRAINT chk_analisis_plagio_motor CHECK (motor IN ('SIMULADO', 'COPYLEAKS')),
    porcentaje            NUMERIC(5,2) CONSTRAINT chk_analisis_plagio_porcentaje CHECK (porcentaje BETWEEN 0 AND 100),
    nivel                 VARCHAR(10)
        CONSTRAINT chk_analisis_plagio_nivel CHECK (nivel IN ('OK', 'MEDIO', 'ALTO')),
    umbral_medio          INT          NOT NULL CONSTRAINT chk_analisis_plagio_umbral_medio CHECK (umbral_medio BETWEEN 0 AND 100),
    umbral_alto           INT          NOT NULL CONSTRAINT chk_analisis_plagio_umbral_alto CHECK (umbral_alto BETWEEN 0 AND 100),
    comparacion_internet  BOOLEAN      NOT NULL,
    documentos_analizados INT          NOT NULL CONSTRAINT chk_analisis_plagio_documentos CHECK (documentos_analizados >= 1),
    mensaje_error         VARCHAR(500),
    scan_id               VARCHAR(36)  CONSTRAINT uq_analisis_plagio_scan UNIQUE,
    id_usuario_inicio     INT          NOT NULL REFERENCES usuario (id_usuario),
    fecha_inicio          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_fin             TIMESTAMPTZ,
    -- Cada estado con exactamente sus datos
    CONSTRAINT chk_analisis_plagio_coherente CHECK (
        (estado = 'EN_PROCESO' AND porcentaje IS NULL AND nivel IS NULL AND fecha_fin IS NULL)
        OR (estado = 'COMPLETADO' AND porcentaje IS NOT NULL AND nivel IS NOT NULL AND fecha_fin IS NOT NULL)
        OR (estado = 'ERROR' AND porcentaje IS NULL AND nivel IS NULL AND mensaje_error IS NOT NULL
            AND fecha_fin IS NOT NULL))
);
-- Un solo analisis EN_PROCESO por trabajo: aunque dos docentes pulsen "Analizar" a la vez, la BD rechaza al
-- segundo (el service ademas lo verifica antes con el trabajo bloqueado y responde 409).
CREATE UNIQUE INDEX uq_analisis_plagio_en_proceso ON analisis_plagio (id_trabajo) WHERE estado = 'EN_PROCESO';
-- Ultimo analisis e historial de un trabajo.
CREATE INDEX idx_analisis_plagio_trabajo ON analisis_plagio (id_trabajo, fecha_inicio DESC);
-- Job de timeout (cada minuto): solo recorre los que siguen en curso.
CREATE INDEX idx_analisis_plagio_en_proceso ON analisis_plagio (fecha_inicio) WHERE estado = 'EN_PROCESO';

-- ---------------------------------------------------------------- analisis_plagio_documento
-- Foto de cada archivo al iniciar (si despues se borra el documento, el analisis conserva nombre y hash).
-- porcentaje NULL = el motor aun no devolvio el resultado de ese documento.
CREATE TABLE analisis_plagio_documento (
    id_analisis_documento SERIAL       PRIMARY KEY,
    id_analisis           INT          NOT NULL REFERENCES analisis_plagio (id_analisis) ON DELETE CASCADE,
    indice                INT          NOT NULL CONSTRAINT chk_analisis_documento_indice CHECK (indice >= 1),
    id_documento          INT          REFERENCES documento (id_documento) ON DELETE SET NULL,
    nombre_archivo        VARCHAR(200) NOT NULL,
    ruta_archivo          VARCHAR(255) NOT NULL,
    sha256                CHAR(64)     NOT NULL,
    porcentaje            NUMERIC(5,2) CONSTRAINT chk_analisis_documento_porcentaje CHECK (porcentaje BETWEEN 0 AND 100),
    fecha_resultado       TIMESTAMPTZ,
    CONSTRAINT uq_analisis_documento_indice UNIQUE (id_analisis, indice)
);

-- ---------------------------------------------------------------- fuente_plagio
-- id_trabajo_fuente: trabajo del repositorio interno que coincide (NULL si no se pudo identificar). Al LEER, una
-- fuente INTERNA cuyo trabajo no esta EVALUADO se muestra sin titulo ni id (contrato: no revelar trabajos ajenos
-- no publicados). url: solo fuentes de internet.
CREATE TABLE fuente_plagio (
    id_fuente         SERIAL        PRIMARY KEY,
    id_analisis       INT           NOT NULL REFERENCES analisis_plagio (id_analisis) ON DELETE CASCADE,
    tipo              VARCHAR(10)   NOT NULL CONSTRAINT chk_fuente_plagio_tipo CHECK (tipo IN ('INTERNA', 'INTERNET')),
    titulo            VARCHAR(300)  NOT NULL,
    url               VARCHAR(1000),
    id_trabajo_fuente INT           REFERENCES trabajo (id_trabajo) ON DELETE SET NULL,
    porcentaje        NUMERIC(5,2)  NOT NULL CONSTRAINT chk_fuente_plagio_porcentaje CHECK (porcentaje BETWEEN 0 AND 100)
);
CREATE INDEX idx_fuente_plagio_analisis ON fuente_plagio (id_analisis);

-- ---------------------------------------------------------------- PLAGIO_GROUP
-- Grupos y nombres declarados SOLO en imp ConstantesDominio (regla 11), con el mismo tipo y rango.
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('PLAGIO_GROUP', 'TIMEOUT_MINUTOS', '30',
     'Minutos maximos de un analisis de plagio en curso; al superarlos pasa a ERROR (entero 1..1440).',
     'sistema', now()),
    ('PLAGIO_GROUP', 'SIMULADO_SEGUNDOS', '8',
     'Segundos que tarda el motor SIMULADO (solo desarrollo) en completar un analisis (entero 1..300).',
     'sistema', now()),
    ('PLAGIO_GROUP', 'MAX_FUENTES', '20',
     'Cantidad maxima de fuentes que se guardan y muestran por analisis, las de mayor porcentaje (entero 1..100).',
     'sistema', now());
