-- =====================================================================================================================
-- Datos de DEMO de la FICCT. SOLO con el perfil "demo" de auth-service (application-demo.yml agrega esta carpeta a
-- spring.flyway.locations). NUNCA en produccion ni en los tests normales. Acordado con el frontend: lo usan los E2E.
--
-- Script REPETIBLE (R__) e IDEMPOTENTE:
--   * Flyway aplica los R__ despues de todas las versionadas (V1..V12 y las futuras) y lo vuelve a aplicar solo si
--     cambia su contenido. Un V1000__ en cambio dejaria "por delante" a las migraciones reales futuras (V13...),
--     que Flyway trataria como fuera de orden y haria fallar la validacion.
--   * Todo INSERT usa ON CONFLICT o NOT EXISTS sobre claves naturales (siglas, usuario, registro, CI): se puede
--     aplicar sobre una BD con datos y re-aplicar sin duplicar nada.
--
-- Contrasenas (BCrypt calculado de antemano, coste 10):
--   doc.maria, doc.jose, 219000001, 219000002 -> Demo2026!     (requiere_cambio_password = false, sin 2FA)
--   padron 219000003 (sin cuenta, para probar el auto-registro) -> Uagrm2026
-- El usuario admin NO se toca aqui (lo crea AdminInicialService); DemoUsuariosListener (perfil demo) le quita el
-- cambio de contrasena obligatorio y el 2FA al arrancar.
-- =====================================================================================================================

-- ---------- Facultad y su configuracion ----------
INSERT INTO facultad (nombre, sigla, estado)
VALUES ('Facultad de Ingenieria en Ciencias de la Computacion y Telecomunicaciones', 'FICCT', 1)
ON CONFLICT (sigla) DO UPDATE SET estado = 1;

INSERT INTO config_facultad (id_facultad, tamano_max_archivo_mb, comparacion_internet, acceso_externo, escala_calif_max)
SELECT f.id_facultad, 10, FALSE, TRUE, 100 FROM facultad f WHERE f.sigla = 'FICCT'
ON CONFLICT (id_facultad) DO UPDATE SET tamano_max_archivo_mb = 10, comparacion_internet = FALSE,
                                        acceso_externo = TRUE, escala_calif_max = 100;

-- ---------- Carreras ----------
INSERT INTO carrera (nombre, sigla, id_facultad)
SELECT c.nombre, c.sigla, f.id_facultad
FROM (VALUES ('Ingenieria Informatica', 'INF'),
             ('Ingenieria en Sistemas', 'SIS'),
             ('Ingenieria en Redes y Telecomunicaciones', 'RED')) AS c (nombre, sigla)
         CROSS JOIN facultad f
WHERE f.sigla = 'FICCT'
ON CONFLICT (sigla) DO NOTHING;

-- ---------- Materias ----------
INSERT INTO materia (nombre, sigla, id_facultad)
SELECT m.nombre, m.sigla, f.id_facultad
FROM (VALUES ('Ingenieria de Software I', 'INF412'),
             ('Ingenieria de Software II', 'INF422'),
             ('Base de Datos I', 'INF312')) AS m (nombre, sigla)
         CROSS JOIN facultad f
WHERE f.sigla = 'FICCT'
ON CONFLICT (id_facultad, sigla) DO UPDATE SET estado = 1;

-- ---------- Semestre 2-2026 (activo) ----------
INSERT INTO semestre (semestre, anio, id_facultad, estado)
SELECT 2, 2026, f.id_facultad, 1 FROM facultad f WHERE f.sigla = 'FICCT'
ON CONFLICT (id_facultad, anio, semestre) DO UPDATE SET estado = 1;

-- ---------- Grupos: INF412-SA, INF412-SB e INF312-SA en 2-2026 ----------
INSERT INTO grupo (nombre, id_materia, id_semestre, estado)
SELECT g.nombre, m.id_materia, s.id_semestre, 1
FROM (VALUES ('SA', 'INF412'), ('SB', 'INF412'), ('SA', 'INF312')) AS g (nombre, materia)
         JOIN facultad f ON f.sigla = 'FICCT'
         JOIN materia m ON m.id_facultad = f.id_facultad AND m.sigla = g.materia
         JOIN semestre s ON s.id_facultad = f.id_facultad AND s.anio = 2026 AND s.semestre = 2
ON CONFLICT (id_materia, id_semestre, nombre) DO UPDATE SET estado = 1;

-- ---------- Fases y tipos de trabajo (activos) ----------
INSERT INTO fase (nombre, descripcion, id_facultad, estado)
SELECT x.nombre, x.descripcion, f.id_facultad, 1
FROM (VALUES ('Perfil', 'Propuesta inicial del trabajo'),
             ('Avance', 'Entrega de avance'),
             ('Final', 'Entrega final')) AS x (nombre, descripcion)
         CROSS JOIN facultad f
WHERE f.sigla = 'FICCT'
ON CONFLICT (id_facultad, nombre) DO UPDATE SET estado = 1;

INSERT INTO tipo_trabajo (nombre, id_facultad, estado)
SELECT t.nombre, f.id_facultad, 1
FROM (VALUES ('Proyecto de materia'), ('Monografia')) AS t (nombre)
         CROSS JOIN facultad f
WHERE f.sigla = 'FICCT'
ON CONFLICT (id_facultad, nombre) DO UPDATE SET estado = 1;

-- ---------- Personas y usuarios (docentes y estudiantes) ----------
INSERT INTO persona (nombre, apellidos, ci, correo, registro, id_carrera)
SELECT p.nombre, p.apellidos, p.ci, p.correo, p.registro, c.id_carrera
FROM (VALUES ('Maria', 'Lopez Suarez', '7000101', 'maria.lopez@uagrm.edu.bo', NULL::INT, NULL),
             ('Jose', 'Rojas Vaca', '7000102', 'jose.rojas@uagrm.edu.bo', NULL::INT, NULL),
             ('Juan', 'Perez Lopez', '7000001', 'juan.perez@uagrm.edu.bo', 219000001, 'INF'),
             ('Ana', 'Gutierrez Rios', '7000002', 'ana.gutierrez@uagrm.edu.bo', 219000002, 'SIS'))
         AS p (nombre, apellidos, ci, correo, registro, carrera)
         LEFT JOIN carrera c ON c.sigla = p.carrera
ON CONFLICT DO NOTHING;

INSERT INTO usuario (usuario, password, requiere_cambio_password, two_factor_enabled, id_tipo_usuario, id_persona,
                     estado)
SELECT u.usuario, '$2a$10$tnLAqMZ0fNiwV/jc4YRbNuG4rAFD.XGoL708AqWGqT4QBbMllQtMC', FALSE, FALSE,
       t.id_tipo_usuario, p.id_persona, 1
FROM (VALUES ('doc.maria', '7000101', 'DOCENTE'),
             ('doc.jose', '7000102', 'DOCENTE'),
             ('219000001', '7000001', 'ESTUDIANTE'),
             ('219000002', '7000002', 'ESTUDIANTE')) AS u (usuario, ci, tipo)
         JOIN persona p ON p.ci = u.ci
         JOIN tipo_usuario t ON t.nombre = u.tipo
ON CONFLICT DO NOTHING;

-- ---------- Docentes de los grupos ----------
-- doc.maria: Administradora de Curso de INF412-SA e INF312-SA. doc.jose: docente (no admin) de INF412-SA.
INSERT INTO grupo_docente (id_grupo, id_usuario, es_admin_curso)
SELECT g.id_grupo, u.id_usuario, d.admin
FROM (VALUES ('doc.maria', 'INF412', 'SA', TRUE),
             ('doc.maria', 'INF312', 'SA', TRUE),
             ('doc.jose', 'INF412', 'SA', FALSE)) AS d (usuario, materia, grupo, admin)
         JOIN usuario u ON u.usuario = d.usuario
         JOIN facultad f ON f.sigla = 'FICCT'
         JOIN materia m ON m.id_facultad = f.id_facultad AND m.sigla = d.materia
         JOIN semestre s ON s.id_facultad = f.id_facultad AND s.anio = 2026 AND s.semestre = 2
         JOIN grupo g ON g.id_materia = m.id_materia AND g.id_semestre = s.id_semestre AND g.nombre = d.grupo
ON CONFLICT (id_grupo, id_usuario) DO UPDATE SET es_admin_curso = EXCLUDED.es_admin_curso;

-- ---------- Padron: los dos estudiantes registrados y uno sin cuenta (219000003, para el auto-registro) ----------
INSERT INTO estudiante_habilitado (registro, ci, correo, password_default, nombres, apellidos, id_carrera, habilitado,
                                   fecha_importacion)
SELECT e.registro, e.ci, e.correo, '$2a$10$MOwgkGJ/5W.5IMvZ0MttAODUuy7JyIgxFRXTyO0rh19IHHNBKr48G', e.nombres,
       e.apellidos, c.id_carrera, TRUE, now()
FROM (VALUES (219000001, '7000001', 'juan.perez@uagrm.edu.bo', 'Juan', 'Perez Lopez', 'INF'),
             (219000002, '7000002', 'ana.gutierrez@uagrm.edu.bo', 'Ana', 'Gutierrez Rios', 'SIS'),
             (219000003, '7000003', 'luis.mamani@uagrm.edu.bo', 'Luis', 'Mamani Choque', 'INF'))
         AS e (registro, ci, correo, nombres, apellidos, carrera)
         JOIN carrera c ON c.sigla = e.carrera
ON CONFLICT DO NOTHING;

-- ---------- Trabajo EVALUADO de ejemplo (Juan, INF412-SA) para que el repositorio no salga vacio ----------
-- Sin archivos en MinIO: un solo documento ENLACE (repositorio de GitHub). El vector de busqueda lo calculan los
-- triggers de V10.
INSERT INTO trabajo (titulo, resumen, estado, calificacion, id_persona_creador, id_grupo, id_materia, id_semestre,
                     id_tipo_trabajo, fecha_envio)
SELECT 'Sistema de gestion de inventarios para PyMEs',
       'Analisis, diseno e implementacion de un sistema web para el control de inventarios de pequenas y medianas '
           || 'empresas, con Spring Boot y Angular.',
       'EVALUADO', 85, p.id_persona, g.id_grupo, m.id_materia, s.id_semestre, t.id_tipo_trabajo, now()
FROM persona p
         JOIN facultad f ON f.sigla = 'FICCT'
         JOIN materia m ON m.id_facultad = f.id_facultad AND m.sigla = 'INF412'
         JOIN semestre s ON s.id_facultad = f.id_facultad AND s.anio = 2026 AND s.semestre = 2
         JOIN grupo g ON g.id_materia = m.id_materia AND g.id_semestre = s.id_semestre AND g.nombre = 'SA'
         JOIN tipo_trabajo t ON t.id_facultad = f.id_facultad AND t.nombre = 'Proyecto de materia'
WHERE p.ci = '7000001'
  AND NOT EXISTS (SELECT 1 FROM trabajo x
                  WHERE x.id_persona_creador = p.id_persona
                    AND x.titulo = 'Sistema de gestion de inventarios para PyMEs');

INSERT INTO trabajo_autor (id_trabajo, id_persona)
SELECT x.id_trabajo, x.id_persona_creador
FROM trabajo x JOIN persona p ON p.id_persona = x.id_persona_creador
WHERE p.ci = '7000001' AND x.titulo = 'Sistema de gestion de inventarios para PyMEs'
ON CONFLICT DO NOTHING;

INSERT INTO hashtag (texto)
VALUES ('springboot'), ('inventarios'), ('angular')
ON CONFLICT (texto) DO NOTHING;

INSERT INTO trabajo_hashtag (id_trabajo, id_hashtag)
SELECT x.id_trabajo, h.id_hashtag
FROM trabajo x
         JOIN persona p ON p.id_persona = x.id_persona_creador
         JOIN hashtag h ON h.texto IN ('springboot', 'inventarios', 'angular')
WHERE p.ci = '7000001' AND x.titulo = 'Sistema de gestion de inventarios para PyMEs'
ON CONFLICT DO NOTHING;

INSERT INTO documento (id_trabajo, id_fase, titulo, descripcion, tipo, categoria, url_externo)
SELECT x.id_trabajo, fa.id_fase, 'Repositorio del proyecto', 'Codigo fuente', 'ENLACE', 'CODIGO',
       'https://github.com/uagrm-ficct/inventarios-pymes'
FROM trabajo x
         JOIN persona p ON p.id_persona = x.id_persona_creador
         JOIN facultad f ON f.sigla = 'FICCT'
         JOIN fase fa ON fa.id_facultad = f.id_facultad AND fa.nombre = 'Final'
WHERE p.ci = '7000001' AND x.titulo = 'Sistema de gestion de inventarios para PyMEs'
  AND NOT EXISTS (SELECT 1 FROM documento d WHERE d.id_trabajo = x.id_trabajo AND d.tipo = 'ENLACE');

-- Historial minimo: la calificacion la registra la Administradora de Curso
INSERT INTO trabajo_historial (id_trabajo, estado_anterior, estado_nuevo, id_usuario, comentario)
SELECT x.id_trabajo, 'APROBADO', 'EVALUADO', u.id_usuario, 'Calificacion: 85'
FROM trabajo x
         JOIN persona p ON p.id_persona = x.id_persona_creador
         JOIN usuario u ON u.usuario = 'doc.maria'
WHERE p.ci = '7000001' AND x.titulo = 'Sistema de gestion de inventarios para PyMEs'
  AND NOT EXISTS (SELECT 1 FROM trabajo_historial h WHERE h.id_trabajo = x.id_trabajo);
