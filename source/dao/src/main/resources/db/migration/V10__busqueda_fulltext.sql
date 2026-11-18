-- Sprint 4: busqueda full-text del repositorio (work-api 1.2.0, GET /api/v1/repositorio y /api/v1/publico/repositorio).
-- Reemplaza la busqueda simple (LIKE con f_unaccent) de V9.

-- ---------------------------------------------------------------- Configuracion de texto es_unaccent
-- Copia de la configuracion 'spanish' (raices: "sistemas" -> "sistem", stopwords en espanol) que antes de sacar la
-- raiz quita las tildes con el diccionario unaccent (extension creada en V9): "Diseño", "diseno" y "DISEÑO" dan el
-- mismo lexema. Solo se cambian los tipos de token con letras no ASCII (word, hword, hword_part); los ASCII
-- (asciiword...) no tienen tildes y siguen yendo directo a spanish_stem.
-- Permisos: crear una configuracion de texto solo exige el privilegio CREATE en el esquema (el dueno de la base lo
-- tiene en public); NO hace falta superusuario. Los diccionarios no tienen ACL: se pueden referenciar libremente.
-- Limitacion conocida: las stopwords de 'spanish' se comparan despues de quitar la tilde, asi que las pocas que
-- llevan tilde ("más", "él", "está") ya no se reconocen como stopwords; solo agrega lexemas poco utiles.
CREATE TEXT SEARCH CONFIGURATION public.es_unaccent (COPY = pg_catalog.spanish);
ALTER TEXT SEARCH CONFIGURATION public.es_unaccent
    ALTER MAPPING FOR hword, hword_part, word WITH public.unaccent, spanish_stem;

-- ---------------------------------------------------------------- Vector de busqueda del trabajo
-- Pesos: titulo A (1.0), hashtags B (0.4), resumen C (0.2) en ts_rank_cd: una coincidencia en el titulo pesa mas.
CREATE OR REPLACE FUNCTION f_trabajo_busqueda(p_id_trabajo INT, p_titulo TEXT, p_resumen TEXT) RETURNS tsvector
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$
    SELECT setweight(to_tsvector('public.es_unaccent', coalesce(p_titulo, '')), 'A')
        || setweight(to_tsvector('public.es_unaccent', coalesce(
               (SELECT string_agg(h.texto, ' ' ORDER BY h.texto)
                FROM trabajo_hashtag th JOIN hashtag h ON h.id_hashtag = th.id_hashtag
                WHERE th.id_trabajo = p_id_trabajo), '')), 'B')
        || setweight(to_tsvector('public.es_unaccent', coalesce(p_resumen, '')), 'C')
$$;

ALTER TABLE trabajo ADD COLUMN busqueda tsvector;

-- Backfill de las filas existentes (antes de los triggers y del NOT NULL).
UPDATE trabajo SET busqueda = f_trabajo_busqueda(id_trabajo, titulo, resumen);
ALTER TABLE trabajo ALTER COLUMN busqueda SET NOT NULL;

-- Mantenimiento con triggers (la BD lo garantiza aunque la fila se cambie fuera de la aplicacion):
-- 1) trabajo: al insertar y al cambiar titulo o resumen se recalcula en la misma fila (BEFORE).
-- 2) trabajo_hashtag: al agregar o quitar un hashtag de un trabajo se recalcula el vector de ESE trabajo (AFTER,
--    por fila). Ese UPDATE solo toca la columna busqueda, asi que no vuelve a disparar el trigger 1.
--    Al borrar un trabajo, sus filas de trabajo_hashtag caen por cascada despues de borrada la fila: el UPDATE
--    no encuentra nada y no hace nada.
-- Los textos de la tabla hashtag no se editan nunca (se crean y se reutilizan), por eso no necesita trigger.
CREATE OR REPLACE FUNCTION trg_trabajo_busqueda() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.busqueda := f_trabajo_busqueda(NEW.id_trabajo, NEW.titulo, NEW.resumen);
    RETURN NEW;
END
$$;

CREATE TRIGGER trabajo_busqueda_insert
    BEFORE INSERT ON trabajo
    FOR EACH ROW EXECUTE FUNCTION trg_trabajo_busqueda();

CREATE TRIGGER trabajo_busqueda_update
    BEFORE UPDATE OF titulo, resumen ON trabajo
    FOR EACH ROW
    WHEN (OLD.titulo IS DISTINCT FROM NEW.titulo OR OLD.resumen IS DISTINCT FROM NEW.resumen)
    EXECUTE FUNCTION trg_trabajo_busqueda();

CREATE OR REPLACE FUNCTION trg_trabajo_hashtag_busqueda() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_id_trabajo INT := CASE WHEN TG_OP = 'DELETE' THEN OLD.id_trabajo ELSE NEW.id_trabajo END;
BEGIN
    UPDATE trabajo t SET busqueda = f_trabajo_busqueda(t.id_trabajo, t.titulo, t.resumen)
    WHERE t.id_trabajo = v_id_trabajo;
    IF TG_OP = 'UPDATE' AND OLD.id_trabajo IS DISTINCT FROM NEW.id_trabajo THEN
        UPDATE trabajo t SET busqueda = f_trabajo_busqueda(t.id_trabajo, t.titulo, t.resumen)
        WHERE t.id_trabajo = OLD.id_trabajo;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trabajo_hashtag_busqueda
    AFTER INSERT OR UPDATE OR DELETE ON trabajo_hashtag
    FOR EACH ROW EXECUTE FUNCTION trg_trabajo_hashtag_busqueda();

-- Indice GIN sobre todo el vector (no parcial por estado: el estado llega como parametro enlazado y con un plan
-- generico PostgreSQL no podria probar el predicado de un indice parcial).
CREATE INDEX idx_trabajo_busqueda ON trabajo USING GIN (busqueda);

-- ---------------------------------------------------------------- Funciones de consulta
-- Envoltorios para usarlos desde JPA Criteria (que no tiene el operador @@). Son SQL de una sola expresion, sin
-- STRICT ni SET: PostgreSQL los "inlinea" y el planner ve "busqueda @@ websearch_to_tsquery(...)", que si usa el
-- indice GIN. websearch_to_tsquery acepta la sintaxis de un buscador: "frase exacta", -excluir, palabra OR otra;
-- nunca lanza error de sintaxis por lo que escriba el usuario.
-- Si la consulta solo tiene stopwords ("de la"), websearch_to_tsquery da una consulta vacia y @@ es falso: la
-- busqueda devuelve 0 resultados (decision documentada en RepositorioService).
CREATE OR REPLACE FUNCTION f_busqueda_coincide(p_busqueda tsvector, p_q TEXT) RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT p_busqueda @@ websearch_to_tsquery('public.es_unaccent', p_q) $$;

-- Relevancia: ts_rank_cd (densidad de cobertura) con los pesos por defecto {D 0.1, C 0.2, B 0.4, A 1.0}.
CREATE OR REPLACE FUNCTION f_busqueda_relevancia(p_busqueda tsvector, p_q TEXT) RETURNS real
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT ts_rank_cd(p_busqueda, websearch_to_tsquery('public.es_unaccent', p_q)) $$;
