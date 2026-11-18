-- Sprint 3 (CU11 basico): busqueda en el repositorio de trabajos EVALUADO (work-api 1.1.0, GET /api/v1/repositorio).
--
-- Decision: busqueda "sin distinguir mayusculas ni tildes" con la extension unaccent de PostgreSQL (contrib,
-- incluida en la imagen oficial postgres:15). Es una extension "trusted" desde PG 13: la puede crear el dueno de
-- la base sin ser superusuario. Se prefirio a translate(lower(...)) porque cubre todos los diacriticos (no solo
-- una lista escrita a mano) y la misma normalizacion se aplica a columna y parametro dentro de la BD.
CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA public;

-- unaccent() es STABLE (depende del diccionario y del search_path), por eso no se puede usar en un indice.
-- Este envoltorio fija el diccionario por nombre calificado y se declara IMMUTABLE: permite indexar expresiones
-- f_unaccent(...) (lo usara la busqueda full-text del Sprint 4) y que el planner lo evalue una sola vez para
-- el parametro. La busqueda simple de este sprint es un LIKE '%texto%' que un indice btree no acelera; se
-- ejecuta solo sobre el subconjunto EVALUADO (ver indice parcial abajo).
CREATE OR REPLACE FUNCTION f_unaccent(texto TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, texto) $$;

-- Repositorio: solo EVALUADO, mas recientes primero (fecha_actualizacion = fecha en que paso a EVALUADO, estado
-- final y no editable). Indice parcial: pequeno y sirve el ORDER BY + LIMIT de cada pagina sin ordenar en memoria.
CREATE INDEX idx_trabajo_evaluado_fecha ON trabajo (fecha_actualizacion DESC, id_trabajo DESC)
    WHERE estado = 'EVALUADO';
-- Filtros frecuentes del repositorio (materia, semestre) dentro del subconjunto EVALUADO.
CREATE INDEX idx_trabajo_evaluado_materia ON trabajo (id_materia, id_semestre) WHERE estado = 'EVALUADO';
