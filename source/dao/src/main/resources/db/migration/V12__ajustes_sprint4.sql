-- Sprint 4 (ajustes): limite de consultas anonimas al repositorio publico (work-service, /api/v1/publico/**).
-- Contador por IP en Redis: work:rate:public:{ip} (ventana fija de 60 s). Al superarlo: 429 con Retry-After.
-- Grupo y nombre declarados SOLO en imp ConstantesDominio (regla 11), con el mismo tipo y rango.
INSERT INTO dominio (domain_group, domain_name, domain_value, description, last_user, last_time) VALUES
    ('WORK_GROUP', 'PUBLICO_PETICIONES_POR_MINUTO', '60',
     'Consultas por minuto permitidas a cada IP en el repositorio publico (sin sesion); al superarlo responde 429 (entero 1..10000).',
     'sistema', now());
