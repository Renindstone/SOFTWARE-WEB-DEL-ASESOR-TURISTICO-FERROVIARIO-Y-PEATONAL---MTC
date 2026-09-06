-- ============================================================================
-- Proyecto : Asesor Turistico Ferroviario y Peatonal - MTC
-- Curso    : Ingenieria de Software - UNU
-- Motor    : PostgreSQL 15
-- Script   : 03_consultas.sql  (VISTAS y FUNCIONES de apoyo)
--
-- Ejecutar DESPUES de 01_esquema.sql y 02_datos.sql
--
-- Objetivo: facilitar la revision de los datos desde DBeaver sin escribir
-- JOINs manualmente cada vez, y encapsular la logica de negocio que luego
-- consumen los servicios de Spring Boot (RutaPeatonalService, AforoService).
--
-- Uso rapido en DBeaver:
--   SELECT * FROM vw_zonas_turisticas;
--   SELECT * FROM fn_buscar_zonas('CUS-OLL', 'Naturaleza');
--   SELECT * FROM fn_verificar_aforo('Llaqta de Machu Picchu', '2026-09-01');
--   SELECT * FROM fn_verificar_rutas();
--   SELECT * FROM fn_verificar_categorias();
--   SELECT * FROM fn_categoria_por_edad('Zona', 15);
--   SELECT * FROM fn_resumen_bd();
-- ============================================================================


-- ============================================================================
-- PARTE 1: VISTAS  (consulta directa, solo lectura)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- vw_zonas_turisticas
-- Zona turistica con su estacion, sus preferencias agrupados y su ruta.
-- Es la vista mas util para revisar de un vistazo el catalogo completo.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_zonas_turisticas AS
SELECT
    z."ZonIdZona"                                       AS id_zona,
    z."ZonNombre"                                       AS zona,
    e."EstNombre"                                       AS estacion,
    e."EstCiudad"                                       AS ciudad,
    z."ZonLatitud"                                      AS latitud_zona,
    z."ZonLongitud"                                     AS longitud_zona,
    string_agg(DISTINCT p."PreNombre", ' + '
               ORDER BY p."PreNombre")                  AS preferencias,
    r."RutDescripcion"                                   AS descripcion_ruta,
    r."RutDistanciaKm"                                  AS km_ida_vuelta,
    r."RutTiempoEstimadoMin"                            AS minutos,
    d."DifNombre"                                       AS dificultad,
    z."ZonCostoAprox"                                   AS costo_zona,
    z."ZonCupoMaximoDiario"                             AS cupo_diario,
    z."ZonEstado"                                       AS estado
FROM zona_turistica z
JOIN estacion e            ON e."EstIdEstacion"    = z."ZonIdEstacionCercana"
LEFT JOIN zona_preferencia zp ON zp."ZprIdZonaTuristica" = z."ZonIdZona"
LEFT JOIN preferencia p   ON p."PreIdPreferencia" = zp."ZprIdPreferencia"
LEFT JOIN ruta_peatonal r  ON r."RutIdZonaDestino" = z."ZonIdZona"
LEFT JOIN dificultad d     ON d."DifIdDificultad"  = r."RutIdDificultad"
GROUP BY z."ZonIdZona", z."ZonNombre", e."EstNombre", e."EstCiudad",
         z."ZonLatitud", z."ZonLongitud",
         r."RutDescripcion", r."RutDistanciaKm", r."RutTiempoEstimadoMin", d."DifNombre",
         z."ZonCostoAprox", z."ZonCupoMaximoDiario", z."ZonEstado";


-- ----------------------------------------------------------------------------
-- vw_servicios_tren
-- Horarios y tarifas de PeruRail con los nombres de estacion resueltos.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_servicios_tren AS
SELECT
    s."SerIdServicio"          AS id_servicio,
    o."EstNombre"              AS origen,
    d."EstNombre"              AS destino,
    s."SerHorarioSalida"       AS salida,
    s."SerHorarioLlegada"      AS llegada,
    s."SerTiempoTransitoMin"   AS minutos,
    s."SerTarifa"              AS tarifa_soles
FROM servicio_tren s
JOIN estacion o ON o."EstIdEstacion" = s."SerIdEstacionOrigen"
JOIN estacion d ON d."EstIdEstacion" = s."SerIdEstacionDestino";


-- ----------------------------------------------------------------------------
-- vw_clima
-- Pronostico del SENAMHI por estacion y fecha.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_clima AS
SELECT
    c."CliFecha"               AS fecha,
    e."EstNombre"              AS estacion,
    e."EstCiudad"              AS ciudad,
    c."CliTemperaturaMinimaC"  AS temperatura_min_c,
    c."CliTemperaturaMaximaC"  AS temperatura_max_c,
    c."CliProbabilidadLluvia"  AS prob_lluvia_pct,
    c."CliEstadoClima"         AS estado_clima
FROM prevision_clima c
JOIN estacion e ON e."EstIdEstacion" = c."CliIdEstacion";


-- ----------------------------------------------------------------------------
-- vw_aforo
-- Estado del cupo diario por zona y fecha (RF-16).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_aforo AS
SELECT
    z."ZonNombre"                                AS zona,
    a."AfoFecha"                                 AS fecha,
    a."AfoCupoUtilizado"                         AS usado,
    z."ZonCupoMaximoDiario"                      AS maximo,
    z."ZonCupoMaximoDiario" - a."AfoCupoUtilizado" AS disponible,
    ROUND(100.0 * a."AfoCupoUtilizado"
          / NULLIF(z."ZonCupoMaximoDiario", 0), 1) AS ocupacion_pct,
    CASE WHEN a."AfoCupoUtilizado" >= z."ZonCupoMaximoDiario"
         THEN 'COMPLETO' ELSE 'DISPONIBLE' END   AS estado
FROM control_aforo a
JOIN zona_turistica z ON z."ZonIdZona" = a."AfoIdZona";


-- ----------------------------------------------------------------------------
-- vw_informes
-- Informes generados con su ruta, zona y usuario (NULL = consulta anonima).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_informes AS
SELECT
    i."InfCodigo"                              AS codigo,
    i."InfFechaEmision"                        AS emitido,
    i."InfFechaVisita"                         AS fecha_visita,
    COALESCE(u."UsuNombreUsuario", '(anonimo)') AS usuario,
    r."RutNombre"                              AS ruta,
    z."ZonNombre"                              AS zona,
    e."EstNombre"                              AS estacion_origen,
    (SELECT COALESCE(SUM(iv."IviCantidad"), 0)
       FROM informe_visitante iv
      WHERE iv."IviIdInforme" = i."InfIdInforme")  AS personas,
    i."InfTotalEstimado"                       AS total_soles
FROM informe_planificacion i
JOIN ruta_peatonal r   ON r."RutIdRuta"      = i."InfIdRuta"
JOIN zona_turistica z  ON z."ZonIdZona"      = r."RutIdZonaDestino"
JOIN estacion e        ON e."EstIdEstacion"  = r."RutIdEstacionOrigen"
LEFT JOIN usuario u    ON u."UsuIdUsuario"   = i."InfIdUsuario";


-- ----------------------------------------------------------------------------
-- vw_auditoria
-- Log de trazabilidad ordenado del mas reciente al mas antiguo (RNF-07).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_auditoria AS
SELECT
    "AudFecha"          AS fecha,
    "AudUsuario"        AS usuario,
    "AudOperacion"      AS operacion,
    "AudTablaAfectada"  AS tabla,
    "AudValorAnterior"  AS valor_anterior,
    "AudValorNuevo"     AS valor_nuevo
FROM auditoria_log
ORDER BY "AudFecha" DESC, "AudIdLog" DESC;


-- ============================================================================
-- PARTE 2: FUNCIONES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- fn_distancia_haversine(lat1, lon1, lat2, lon2)
-- Distancia en km en linea recta entre dos coordenadas.
-- Es la formula que implementa RutaPeatonalService (seccion 5.1 del documento).
-- ----------------------------------------------------------------------------
-- NOTA: las funciones trigonometricas de PostgreSQL devuelven DOUBLE PRECISION,
-- por lo que el resultado se convierte a NUMERIC antes de aplicar ROUND(x, 2).
CREATE OR REPLACE FUNCTION fn_distancia_haversine(
    lat1 NUMERIC, lon1 NUMERIC,
    lat2 NUMERIC, lon2 NUMERIC
) RETURNS NUMERIC AS $$
DECLARE
    radio_tierra CONSTANT DOUBLE PRECISION := 6371;  -- km
    d_lat  DOUBLE PRECISION;
    d_lon  DOUBLE PRECISION;
    a      DOUBLE PRECISION;
BEGIN
    d_lat := RADIANS(lat2 - lat1);
    d_lon := RADIANS(lon2 - lon1);
    a := SIN(d_lat / 2) ^ 2
         + COS(RADIANS(lat1)) * COS(RADIANS(lat2)) * SIN(d_lon / 2) ^ 2;
    RETURN ROUND((radio_tierra * 2 * ATAN2(SQRT(a), SQRT(1 - a)))::NUMERIC, 2);
END;
$$ LANGUAGE plpgsql IMMUTABLE;


-- ----------------------------------------------------------------------------
-- fn_buscar_zonas(codigo_estacion, preferencia, dificultad)
-- Busqueda principal del turista (RF-03).
--   - p_preferencia y p_dificultad son opcionales: pasar NULL para no filtrar.
--   - Excluye estaciones y zonas inactivas (RF-02).
-- Ejemplos:
--   SELECT * FROM fn_buscar_zonas('CUS-OLL', 'Naturaleza', NULL);
--   SELECT * FROM fn_buscar_zonas('CUS-SPD', NULL, 'Baja');
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_buscar_zonas(
    p_codigo_estacion VARCHAR,
    p_preferencia     VARCHAR DEFAULT NULL,
    p_dificultad      VARCHAR DEFAULT NULL
) RETURNS TABLE (
    zona            VARCHAR,
    preferencias   TEXT,
    descripcion_ruta VARCHAR,
    km_ida_vuelta   NUMERIC,
    minutos         INTEGER,
    dificultad      VARCHAR,
    costo_zona      NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT z."ZonNombre",
           string_agg(DISTINCT p2."PreNombre", ' + ' ORDER BY p2."PreNombre"),
           r."RutDescripcion",
           r."RutDistanciaKm",
           r."RutTiempoEstimadoMin",
           d."DifNombre",
           z."ZonCostoAprox"
    FROM zona_turistica z
    JOIN estacion e           ON e."EstIdEstacion"      = z."ZonIdEstacionCercana"
    JOIN ruta_peatonal r      ON r."RutIdZonaDestino"   = z."ZonIdZona"
    JOIN dificultad d         ON d."DifIdDificultad"    = r."RutIdDificultad"
    JOIN zona_preferencia zp ON zp."ZprIdZonaTuristica" = z."ZonIdZona"
    JOIN preferencia p2      ON p2."PreIdPreferencia"  = zp."ZprIdPreferencia"
    WHERE e."EstCodigo" = p_codigo_estacion
      AND e."EstEstado" = 'Activa'
      AND z."ZonEstado" = 'Activa'
      AND (p_dificultad IS NULL OR d."DifNombre" = p_dificultad)
      AND (p_preferencia IS NULL OR EXISTS (
            SELECT 1
            FROM zona_preferencia zp2
            JOIN preferencia p3 ON p3."PreIdPreferencia" = zp2."ZprIdPreferencia"
            WHERE zp2."ZprIdZonaTuristica" = z."ZonIdZona"
              AND p3."PreNombre" = p_preferencia))
    GROUP BY z."ZonNombre", r."RutDescripcion", r."RutDistanciaKm", r."RutTiempoEstimadoMin",
             d."DifNombre", z."ZonCostoAprox"
    ORDER BY r."RutDistanciaKm";
END;
$$ LANGUAGE plpgsql;


-- ----------------------------------------------------------------------------
-- fn_verificar_aforo(nombre_zona, fecha)
-- Consulta el estado del cupo diario de una zona (RF-16, apoyo a CN-09).
-- Devuelve 'SIN CONTROL' si la zona no maneja aforo.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_verificar_aforo(
    p_zona  VARCHAR,
    p_fecha DATE
) RETURNS TABLE (
    zona       VARCHAR,
    fecha      DATE,
    usado      INTEGER,
    maximo     INTEGER,
    disponible INTEGER,
    estado     TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT z."ZonNombre",
           p_fecha,
           COALESCE(a."AfoCupoUtilizado", 0),
           z."ZonCupoMaximoDiario",
           z."ZonCupoMaximoDiario" - COALESCE(a."AfoCupoUtilizado", 0),
           CASE
               WHEN z."ZonCupoMaximoDiario" IS NULL THEN 'SIN CONTROL'
               WHEN COALESCE(a."AfoCupoUtilizado", 0) >= z."ZonCupoMaximoDiario"
                    THEN 'COMPLETO'
               ELSE 'DISPONIBLE'
           END
    FROM zona_turistica z
    LEFT JOIN control_aforo a
           ON a."AfoIdZona" = z."ZonIdZona"
          AND a."AfoFecha"  = p_fecha
    WHERE z."ZonNombre" = p_zona;
END;
$$ LANGUAGE plpgsql;


-- ----------------------------------------------------------------------------
-- fn_itinerario(codigo_estacion, fecha)
-- Arma el "informe consolidado" de una estacion: zonas alcanzables a pie,
-- clima del dia, aforo y costo total estimado (RF-08).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_itinerario(
    p_codigo_estacion VARCHAR,
    p_fecha           DATE
) RETURNS TABLE (
    zona             VARCHAR,
    descripcion_ruta VARCHAR,
    km_ida_vuelta    NUMERIC,
    minutos          INTEGER,
    dificultad       VARCHAR,
    clima            VARCHAR,
    temperatura_min_c NUMERIC,
    temperatura_max_c NUMERIC,
    aforo            TEXT,
    costo_total      NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT z."ZonNombre",
           r."RutDescripcion",
           r."RutDistanciaKm",
           r."RutTiempoEstimadoMin",
           d."DifNombre",
           c."CliEstadoClima",
           c."CliTemperaturaMinimaC",
           c."CliTemperaturaMaximaC",
           CASE
               WHEN z."ZonCupoMaximoDiario" IS NULL THEN 'SIN CONTROL'
               WHEN COALESCE(a."AfoCupoUtilizado", 0) >= z."ZonCupoMaximoDiario"
                    THEN 'COMPLETO'
               ELSE 'DISPONIBLE'
           END,
           COALESCE(z."ZonCostoAprox", 0)
           + COALESCE((SELECT MIN(s."SerTarifa")
                       FROM servicio_tren s
                       WHERE s."SerIdEstacionDestino" = e."EstIdEstacion"), 0)
    FROM zona_turistica z
    JOIN estacion e          ON e."EstIdEstacion"    = z."ZonIdEstacionCercana"
    JOIN ruta_peatonal r     ON r."RutIdZonaDestino" = z."ZonIdZona"
    JOIN dificultad d        ON d."DifIdDificultad" = r."RutIdDificultad"
    LEFT JOIN prevision_clima c
           ON c."CliIdEstacion" = e."EstIdEstacion" AND c."CliFecha" = p_fecha
    LEFT JOIN control_aforo a
           ON a."AfoIdZona" = z."ZonIdZona" AND a."AfoFecha" = p_fecha
    WHERE e."EstCodigo" = p_codigo_estacion
      AND e."EstEstado" = 'Activa'
      AND z."ZonEstado" = 'Activa'
    ORDER BY r."RutDistanciaKm";
END;
$$ LANGUAGE plpgsql;


-- ----------------------------------------------------------------------------
-- vw_informe_visitantes
-- Composicion del grupo de cada informe, con la categoria aplicada en cada
-- ambito. Permite revisar de un vistazo por que un informe costo lo que costo.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_informe_visitantes AS
SELECT
    i."InfCodigo"                                      AS informe,
    iv."IviEdad"                                       AS edad,
    iv."IviCantidad"                                   AS personas,
    COALESCE(ct."CatNombre", '(sin tren)')             AS categoria_tren,
    cz."CatNombre"                                     AS categoria_zona,
    iv."IviSubtotalTren"                               AS subtotal_tren,
    iv."IviSubtotalZona"                               AS subtotal_zona,
    iv."IviSubtotalTren" + iv."IviSubtotalZona"        AS subtotal
FROM informe_visitante iv
JOIN informe_planificacion i  ON i."InfIdInforme"  = iv."IviIdInforme"
LEFT JOIN categoria_visitante ct ON ct."CatIdCategoria" = iv."IviIdCategoriaTren"
JOIN categoria_visitante cz   ON cz."CatIdCategoria" = iv."IviIdCategoriaZona"
ORDER BY i."InfCodigo", iv."IviEdad" DESC;


-- ----------------------------------------------------------------------------
-- fn_categoria_por_edad(ambito, edad)
-- Categoria tarifaria que corresponde a una edad en un ambito ('Tren'/'Zona').
-- Es la regla que aplicara la capa de negocio al armar el informe de un grupo.
--   SELECT * FROM fn_categoria_por_edad('Tren', 15);  -- Adulto
--   SELECT * FROM fn_categoria_por_edad('Zona', 15);  -- Nino
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_categoria_por_edad(
    p_ambito VARCHAR,
    p_edad   INTEGER
) RETURNS TABLE (
    id_categoria  INTEGER,
    ambito        VARCHAR,
    categoria     VARCHAR,
    factor_precio NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT c."CatIdCategoria", c."CatAmbito", c."CatNombre", c."CatFactorPrecio"
    FROM categoria_visitante c
    WHERE c."CatAmbito" = p_ambito
      AND p_edad >= c."CatEdadMinima"
      AND (c."CatEdadMaxima" IS NULL OR p_edad <= c."CatEdadMaxima");
END;
$$ LANGUAGE plpgsql STABLE;


-- ----------------------------------------------------------------------------
-- fn_verificar_categorias()
-- La restriccion EXCLUDE de categoria_visitante impide que dos tramos de edad
-- se solapen, pero no que quede un hueco (por ejemplo, si alguien edita el
-- corte de nino de 11 a 10 y olvida bajar el de adulto). Esta funcion recorre
-- las edades 0..120 y avisa de las que no tienen categoria en algun ambito.
--   SELECT * FROM fn_verificar_categorias();   -- sin filas = correcto
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_verificar_categorias()
RETURNS TABLE (ambito VARCHAR, edad INTEGER, problema TEXT) AS $$
BEGIN
    RETURN QUERY
    SELECT a."CatAmbito", e.edad, 'Edad sin categoria tarifaria'::TEXT
    FROM (SELECT DISTINCT "CatAmbito" FROM categoria_visitante) a
    CROSS JOIN generate_series(0, 120) AS e(edad)
    WHERE NOT EXISTS (
        SELECT 1 FROM categoria_visitante c
        WHERE c."CatAmbito" = a."CatAmbito"
          AND e.edad >= c."CatEdadMinima"
          AND (c."CatEdadMaxima" IS NULL OR e.edad <= c."CatEdadMaxima"))
    ORDER BY 1, 2;
END;
$$ LANGUAGE plpgsql STABLE;


-- ----------------------------------------------------------------------------
-- fn_resumen_bd()
-- Conteo de registros por tabla. Util para verificar que la carga de datos
-- se ejecuto completa despues de levantar el contenedor.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_resumen_bd()
RETURNS TABLE (tabla TEXT, registros BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT 'rol'::TEXT,                   COUNT(*) FROM rol
    UNION ALL SELECT 'usuario',           COUNT(*) FROM usuario
    UNION ALL SELECT 'preferencia',      COUNT(*) FROM preferencia
    UNION ALL SELECT 'dificultad',        COUNT(*) FROM dificultad
    UNION ALL SELECT 'categoria_visitante', COUNT(*) FROM categoria_visitante
    UNION ALL SELECT 'estacion',          COUNT(*) FROM estacion
    UNION ALL SELECT 'servicio_tren',     COUNT(*) FROM servicio_tren
    UNION ALL SELECT 'zona_turistica',    COUNT(*) FROM zona_turistica
    UNION ALL SELECT 'zona_preferencia', COUNT(*) FROM zona_preferencia
    UNION ALL SELECT 'ruta_peatonal',     COUNT(*) FROM ruta_peatonal
    UNION ALL SELECT 'prevision_clima',   COUNT(*) FROM prevision_clima
    UNION ALL SELECT 'informe_planificacion', COUNT(*) FROM informe_planificacion
    UNION ALL SELECT 'informe_visitante', COUNT(*) FROM informe_visitante
    UNION ALL SELECT 'control_aforo',     COUNT(*) FROM control_aforo
    UNION ALL SELECT 'auditoria_log',     COUNT(*) FROM auditoria_log;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- fn_verificar_rutas()
-- Contrasta la distancia registrada en ruta_peatonal contra el calculo
-- Haversine (estacion de origen -> zona, x2) que hace RutaPeatonalService
-- (seccion 5.1). Sirve para detectar rutas cuyos valores quedaron
-- desalineados de las coordenadas tras una edicion manual.
--   SELECT * FROM fn_verificar_rutas() WHERE estado <> 'OK';
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_verificar_rutas()
RETURNS TABLE (
    ruta            VARCHAR,
    km_registrados  NUMERIC,
    km_haversine    NUMERIC,
    diferencia_km   NUMERIC,
    estado          TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT r."RutNombre",
           r."RutDistanciaKm",
           ROUND(2 * fn_distancia_haversine(e."EstLatitud", e."EstLongitud",
                                            z."ZonLatitud", z."ZonLongitud"), 2),
           ROUND(ABS(r."RutDistanciaKm"
                     - 2 * fn_distancia_haversine(e."EstLatitud", e."EstLongitud",
                                                  z."ZonLatitud", z."ZonLongitud")), 2),
           CASE WHEN ABS(r."RutDistanciaKm"
                         - 2 * fn_distancia_haversine(e."EstLatitud", e."EstLongitud",
                                                      z."ZonLatitud", z."ZonLongitud")) <= 0.05
                THEN 'OK' ELSE 'REVISAR' END
    FROM ruta_peatonal r
    JOIN estacion e       ON e."EstIdEstacion" = r."RutIdEstacionOrigen"
    JOIN zona_turistica z ON z."ZonIdZona"     = r."RutIdZonaDestino"
    ORDER BY 4 DESC;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- FIN DEL SCRIPT DE VISTAS Y FUNCIONES
-- ============================================================================
