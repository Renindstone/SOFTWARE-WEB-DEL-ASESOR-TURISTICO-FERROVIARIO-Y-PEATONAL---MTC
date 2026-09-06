-- ============================================================================
-- Proyecto : Asesor Turistico Ferroviario y Peatonal - MTC
-- Curso    : Ingenieria de Software - UNU
-- Script   : 99_reset_bd.sql  (LIMPIEZA - uso en desarrollo)
--
-- !! ADVERTENCIA !!
-- Este script BORRA datos de forma irreversible. No ejecutar en produccion.
-- El prefijo 99 evita que Docker lo corra antes que 01_esquema.sql.
--
-- Contiene tres opciones. Ejecutar SOLO UNA de ellas.
-- ============================================================================


-- ============================================================================
-- OPCION A  (recomendada) - Bloque dinamico
-- Recorre el catalogo de PostgreSQL y elimina toda tabla del esquema public,
-- sin importar cuantas sean ni como se llamen. No hay que mantener la lista.
-- CASCADE arrastra las llaves foraneas, indices y secuencias asociadas.
-- ============================================================================

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
        RAISE NOTICE 'Tabla eliminada: %', r.tablename;
    END LOOP;
END $$;


-- ============================================================================
-- OPCION B - Reset total del esquema
-- Mas agresivo: elimina el esquema completo y lo recrea vacio. Ademas de las
-- tablas, borra vistas, funciones, secuencias y tipos de dato personalizados.
-- Util cuando el esquema quedo en un estado inconsistente.
-- ============================================================================

-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public AUTHORIZATION admin_mtc;
-- GRANT ALL ON SCHEMA public TO admin_mtc;
-- GRANT ALL ON SCHEMA public TO public;
-- COMMENT ON SCHEMA public IS 'standard public schema';


-- ============================================================================
-- OPCION C - Eliminacion explicita en orden inverso de dependencias
-- Documenta el grafo de dependencias del modelo. Se elimina primero lo que
-- referencia y al final lo referenciado, para no violar las llaves foraneas.
-- ============================================================================

-- DROP TABLE IF EXISTS auditoria_log;
-- DROP TABLE IF EXISTS control_aforo;
-- DROP TABLE IF EXISTS informe_visitante;
-- DROP TABLE IF EXISTS informe_planificacion;
-- DROP TABLE IF EXISTS prevision_clima;
-- DROP TABLE IF EXISTS ruta_peatonal;
-- DROP TABLE IF EXISTS servicio_tren;
-- DROP TABLE IF EXISTS zona_turistica;
-- DROP TABLE IF EXISTS estacion;
-- DROP TABLE IF EXISTS categoria_visitante;
-- DROP TABLE IF EXISTS dificultad;
-- DROP TABLE IF EXISTS preferencia;
-- DROP TABLE IF EXISTS usuario;
-- DROP TABLE IF EXISTS rol;


-- ============================================================================
-- VERIFICACION - debe devolver 0 filas
-- ============================================================================

SELECT tablename FROM pg_tables WHERE schemaname = 'public';
