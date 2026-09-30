-- Roles del sistema. DOCENTE_ADMIN_CURSO se maneja con el flag es_admin_curso en grupo_docente (Sprint 2).
INSERT INTO tipo_usuario (nombre, descripcion) VALUES
    ('ADMIN',      'Gestion total del sistema'),
    ('DOCENTE',    'Revision y calificacion de trabajos de sus grupos'),
    ('ESTUDIANTE', 'Sube y consulta trabajos');
