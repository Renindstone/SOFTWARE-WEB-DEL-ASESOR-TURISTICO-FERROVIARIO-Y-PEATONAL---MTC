
-- ============================================================================
-- Proyecto : Asesor Turistico Ferroviario y Peatonal - MTC
-- Curso    : Ingenieria de Software - UNU
-- Motor    : PostgreSQL 15
-- Script   : 01_esquema.sql  (DDL - creacion de tablas)
--
-- Corresponde a la seccion 6.2 (Diseno Fisico) y 6.3 (Diccionario de Datos)
-- del documento del proyecto.
--
-- Convencion: tablas en snake_case minuscula; columnas con el prefijo
-- normalizado de 3-4 letras exigido por la catedra, entre comillas dobles
-- para preservar el uso de mayusculas en PostgreSQL.
-- ============================================================================

-- btree_gist permite combinar la igualdad de "CatAmbito" con el solapamiento
-- de rangos en la restriccion EXCLUDE de categoria_visitante. Viene con la
-- imagen oficial de PostgreSQL (paquete contrib).
CREATE EXTENSION IF NOT EXISTS btree_gist;
 
-- ----------------------------------------------------------------------------
-- 1. ROL  (seguridad)
-- ----------------------------------------------------------------------------
CREATE TABLE rol (
    "RolIdRol"        INTEGER GENERATED ALWAYS AS IDENTITY,
    "RolNombreRol"    VARCHAR(30)   NOT NULL,
    CONSTRAINT pk_rol            PRIMARY KEY ("RolIdRol"),
    CONSTRAINT uq_rol_nombre     UNIQUE ("RolNombreRol")
);
 
-- ----------------------------------------------------------------------------
-- 2. USUARIO  (seguridad)
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
    "UsuIdUsuario"      INTEGER GENERATED ALWAYS AS IDENTITY,
    "UsuNombreUsuario"  VARCHAR(50)   NOT NULL,
    "UsuContrasenia"    VARCHAR(100)  NOT NULL,
    "UsuNombre"         VARCHAR(50)   NOT NULL,
    "UsuApellidos"      VARCHAR(50)   NOT NULL,
    "UsuEmail"          VARCHAR(50)   NOT NULL,
    "UsuIdRol"          INTEGER       NOT NULL,
    "UsuEstado"         VARCHAR(10)   NOT NULL DEFAULT 'Activo',
    CONSTRAINT pk_usuario        PRIMARY KEY ("UsuIdUsuario"),
    CONSTRAINT uq_usuario_nombre UNIQUE ("UsuNombreUsuario"),
    CONSTRAINT uq_usuario_email  UNIQUE ("UsuEmail"),
    CONSTRAINT fk_usuario_rol    FOREIGN KEY ("UsuIdRol")
        REFERENCES rol ("RolIdRol") ON DELETE RESTRICT,
    CONSTRAINT ck_usuario_estado CHECK ("UsuEstado" IN ('Activo', 'Inactivo'))
);
 
-- ----------------------------------------------------------------------------
-- 3. TIPO_TURISMO  (parametrica - RNF-06 Escalabilidad)
-- ----------------------------------------------------------------------------
CREATE TABLE tipo_turismo (
    "TipIdTipoTurismo"  INTEGER GENERATED ALWAYS AS IDENTITY,
    "TipNombre"         VARCHAR(30)   NOT NULL,
    "TipDescripcion"    VARCHAR(150)  NULL,
    CONSTRAINT pk_tipo_turismo    PRIMARY KEY ("TipIdTipoTurismo"),
    CONSTRAINT uq_tipo_nombre     UNIQUE ("TipNombre")
);
 
-- ----------------------------------------------------------------------------
-- 4. DIFICULTAD  (parametrica - RNF-06 Escalabilidad)
--
-- RutDificultad salia de esta tabla como texto con un CHECK. Se separa por dos
-- razones. La primera es de modelo: el nivel de dificultad es una entidad del
-- dominio con atributos propios, no una etiqueta de la ruta. La segunda la
-- exige el RNF-06, que pide "modificar los parametros de dificultad sin
-- alterar el codigo fuente principal, mediante tablas parametricas"; hasta
-- ahora los umbrales y el ritmo de caminata eran constantes escritas en
-- RutaPeatonalService.
--
-- DifDistanciaMaximaKm es el tope del tramo (ida y vuelta) que todavia se
-- clasifica en ese nivel; la dificultad mas alta lo deja en NULL por no tener
-- tope. DifOrden permite comparar niveles ("dificultad maxima aceptada" del
-- turista) sin depender del texto del nombre.
-- ----------------------------------------------------------------------------
CREATE TABLE dificultad (
    "DifIdDificultad"       INTEGER GENERATED ALWAYS AS IDENTITY,
    "DifNombre"             VARCHAR(10)    NOT NULL,
    "DifDescripcion"        VARCHAR(150)   NULL,
    "DifOrden"              SMALLINT       NOT NULL,
    "DifDistanciaMaximaKm"  NUMERIC(5,2)   NULL,
    "DifVelocidadMinPorKm"  SMALLINT       NOT NULL,
    CONSTRAINT pk_dificultad          PRIMARY KEY ("DifIdDificultad"),
    CONSTRAINT uq_dificultad_nombre   UNIQUE ("DifNombre"),
    CONSTRAINT uq_dificultad_orden    UNIQUE ("DifOrden"),
    CONSTRAINT ck_dificultad_orden    CHECK ("DifOrden" > 0),
    CONSTRAINT ck_dificultad_distancia CHECK ("DifDistanciaMaximaKm" IS NULL
                                              OR "DifDistanciaMaximaKm" > 0),
    CONSTRAINT ck_dificultad_velocidad CHECK ("DifVelocidadMinPorKm" > 0)
);
 
-- ----------------------------------------------------------------------------
-- 5. CATEGORIA_VISITANTE  (parametrica - tarifas por edad)
--
-- Ni PeruRail ni el santuario cobran lo mismo a todo el mundo, y no usan los
-- mismos cortes de edad, por eso la tabla lleva el ambito:
--   Tren (PeruRail): infante 0-2 no paga, nino 3-11 paga la mitad, adulto 12+.
--   Zona (tarifa del santuario): nino 3-17 con tarifa reducida, adulto 18+.
-- Un unico juego de rangos habria sido incorrecto: un chico de 15 anos paga
-- tarifa de adulto en el tren y de nino en la zona.
--
-- CatFactorPrecio se aplica sobre el precio base del servicio (SerTarifa) o de
-- la zona (ZonCostoAprox). Es una simplificacion coherente con el alcance del
-- proyecto -- el propio diccionario llama "aproximado" al costo de la zona --;
-- si en el futuro se necesita el precio exacto por zona y categoria, el paso
-- siguiente es una tabla de tarifas (zona x categoria) en vez del factor.
--
-- La tarifa de estudiante NO se modela aqui a proposito: no depende de la edad
-- sino de un carne vigente que se acredita en el ingreso, asi que no puede
-- convivir con rangos de edad excluyentes.
-- ----------------------------------------------------------------------------
CREATE TABLE categoria_visitante (
    "CatIdCategoria"   INTEGER GENERATED ALWAYS AS IDENTITY,
    "CatAmbito"        VARCHAR(10)    NOT NULL,
    "CatNombre"        VARCHAR(30)    NOT NULL,
    "CatEdadMinima"    SMALLINT       NOT NULL,
    "CatEdadMaxima"    SMALLINT       NULL,
    "CatFactorPrecio"  NUMERIC(5,4)   NOT NULL,
    "CatDescripcion"   VARCHAR(150)   NULL,
    CONSTRAINT pk_categoria_visitante  PRIMARY KEY ("CatIdCategoria"),
    CONSTRAINT uq_categoria_ambito_nombre UNIQUE ("CatAmbito", "CatNombre"),
    CONSTRAINT ck_categoria_ambito     CHECK ("CatAmbito" IN ('Tren', 'Zona')),
    CONSTRAINT ck_categoria_edad_min   CHECK ("CatEdadMinima" >= 0),
    CONSTRAINT ck_categoria_edad_rango CHECK ("CatEdadMaxima" IS NULL
                                              OR "CatEdadMaxima" >= "CatEdadMinima"),
    CONSTRAINT ck_categoria_factor     CHECK ("CatFactorPrecio" BETWEEN 0 AND 1),
    -- Dentro de un mismo ambito los tramos de edad no pueden solaparse: si lo
    -- hicieran, una misma edad tendria dos precios validos a la vez.
    CONSTRAINT ex_categoria_sin_solape EXCLUDE USING gist (
        "CatAmbito" WITH =,
        int4range("CatEdadMinima", COALESCE("CatEdadMaxima" + 1, 2147483647)) WITH &&
    )
);
 
-- ----------------------------------------------------------------------------
-- 6. ESTACION  (nucleo - fuente PeruRail)
-- ----------------------------------------------------------------------------
CREATE TABLE estacion (
    "EstIdEstacion"   INTEGER GENERATED ALWAYS AS IDENTITY,
    "EstCodigo"       VARCHAR(10)    NOT NULL,
    "EstNombre"       VARCHAR(80)    NOT NULL,
    "EstLatitud"      NUMERIC(9,6)   NOT NULL,
    "EstLongitud"     NUMERIC(9,6)   NOT NULL,
    "EstAltitud"      NUMERIC(6,2)   NULL,
    "EstCiudad"       VARCHAR(50)    NOT NULL,
    "EstEstado"       VARCHAR(10)    NOT NULL DEFAULT 'Activa',
    CONSTRAINT pk_estacion         PRIMARY KEY ("EstIdEstacion"),
    CONSTRAINT uq_estacion_codigo  UNIQUE ("EstCodigo"),
    CONSTRAINT ck_estacion_estado  CHECK ("EstEstado" IN ('Activa', 'Inactiva'))
);
 
-- ----------------------------------------------------------------------------
-- 7. SERVICIO_TREN  (integracion PeruRail)
-- ----------------------------------------------------------------------------
CREATE TABLE servicio_tren (
    "SerIdServicio"          INTEGER GENERATED ALWAYS AS IDENTITY,
    "SerHorarioSalida"       TIME           NOT NULL,
    "SerHorarioLlegada"      TIME           NOT NULL,
    "SerTiempoTransitoMin"   INTEGER        NOT NULL,
    "SerTarifa"              NUMERIC(7,2)   NOT NULL,
    "SerIdEstacionOrigen"    INTEGER        NOT NULL,
    "SerIdEstacionDestino"   INTEGER        NOT NULL,
    CONSTRAINT pk_servicio_tren        PRIMARY KEY ("SerIdServicio"),
    CONSTRAINT fk_servicio_est_origen  FOREIGN KEY ("SerIdEstacionOrigen")
        REFERENCES estacion ("EstIdEstacion") ON DELETE RESTRICT,
    CONSTRAINT fk_servicio_est_destino FOREIGN KEY ("SerIdEstacionDestino")
        REFERENCES estacion ("EstIdEstacion") ON DELETE RESTRICT,
    CONSTRAINT ck_servicio_tiempo      CHECK ("SerTiempoTransitoMin" > 0),
    CONSTRAINT ck_servicio_tarifa      CHECK ("SerTarifa" >= 0),
    -- Un tramo no puede tener dos salidas a la misma hora. Ademas da a la
    -- sincronizacion con PeruRail una clave natural con la que reconocer un
    -- servicio ya cargado, en vez de volver a insertarlo en cada ejecucion.
    CONSTRAINT uq_servicio_tramo_hora  UNIQUE ("SerIdEstacionOrigen",
                                               "SerIdEstacionDestino",
                                               "SerHorarioSalida")
);
 
-- ----------------------------------------------------------------------------
-- 8. ZONA_TURISTICA  (fuente Travel Group Peru)
--
-- NOTA 1: la columna "ZonIdTipoTurismo" fue retirada de esta tabla. La
-- categorizacion turistica pasa a resolverse mediante la tabla intermedia
-- zona_tipo_turismo (punto 9), ya que una misma zona puede pertenecer a mas
-- de una categoria a la vez (relacion N:M).
--
-- NOTA 2: "ZonLatitud"/"ZonLongitud" son la ubicacion propia del punto de
-- interes. La seccion 5.1 del documento define que RutaPeatonalService calcula
-- la distancia "a partir de las coordenadas de ambos puntos" (estacion y zona)
-- con la formula de Haversine, y que el mapa Leaflet dibuja la estacion de
-- origen Y la zona de destino; sin estas dos columnas la unica coordenada
-- disponible para la zona seria la de su estacion cercana, con lo que toda
-- ruta partiendo de esa misma estacion daria distancia cero. El diccionario de
-- datos (6.4) las omite: se documentan aqui como complemento necesario.
-- ----------------------------------------------------------------------------
CREATE TABLE zona_turistica (
    "ZonIdZona"             INTEGER GENERATED ALWAYS AS IDENTITY,
    "ZonNombre"             VARCHAR(100)   NOT NULL,
    "ZonDescripcion"        VARCHAR(500)   NULL,
    "ZonLatitud"            NUMERIC(9,6)   NOT NULL,
    "ZonLongitud"           NUMERIC(9,6)   NOT NULL,
    "ZonIdEstacionCercana"  INTEGER        NOT NULL,
    "ZonCostoAprox"         NUMERIC(7,2)   NULL,
    "ZonCupoMaximoDiario"   INTEGER        NULL,
    "ZonEstado"             VARCHAR(10)    NOT NULL DEFAULT 'Activa',
    CONSTRAINT pk_zona_turistica    PRIMARY KEY ("ZonIdZona"),
    -- 02_datos.sql resuelve las llaves foraneas de zona_tipo_turismo y
    -- ruta_peatonal haciendo JOIN por "ZonNombre": si hubiera dos zonas con el
    -- mismo nombre, esa carga duplicaria filas en silencio.
    CONSTRAINT uq_zona_nombre       UNIQUE ("ZonNombre"),
    CONSTRAINT fk_zona_estacion     FOREIGN KEY ("ZonIdEstacionCercana")
        REFERENCES estacion ("EstIdEstacion") ON DELETE RESTRICT,
    CONSTRAINT ck_zona_estado       CHECK ("ZonEstado" IN ('Activa', 'Inactiva')),
    CONSTRAINT ck_zona_cupo         CHECK ("ZonCupoMaximoDiario" IS NULL
                                           OR "ZonCupoMaximoDiario" > 0),
    CONSTRAINT ck_zona_latitud      CHECK ("ZonLatitud"  BETWEEN -90  AND 90),
    CONSTRAINT ck_zona_longitud     CHECK ("ZonLongitud" BETWEEN -180 AND 180)
);
 
-- ----------------------------------------------------------------------------
-- 9. ZONA_TIPO_TURISMO  (tabla intermedia N:M)
--
-- Resuelve la relacion muchos a muchos entre zona_turistica y tipo_turismo.
-- Ejemplo: la Fortaleza de Ollantaytambo puede clasificarse simultaneamente
-- como "Historia/Cultura" y como "Naturaleza", y debe aparecer en las
-- busquedas de ambas preferencias (RF-03).
-- ----------------------------------------------------------------------------
CREATE TABLE zona_tipo_turismo (
    "ZtiIdZonaTipo"        INTEGER GENERATED ALWAYS AS IDENTITY,
    "ZtiIdZonaTuristica"   INTEGER   NOT NULL,
    "ZtiIdTipoTurismo"     INTEGER   NOT NULL,
    CONSTRAINT pk_zona_tipo_turismo    PRIMARY KEY ("ZtiIdZonaTipo"),
    CONSTRAINT fk_zti_zona             FOREIGN KEY ("ZtiIdZonaTuristica")
        REFERENCES zona_turistica ("ZonIdZona") ON DELETE CASCADE,
    CONSTRAINT fk_zti_tipo             FOREIGN KEY ("ZtiIdTipoTurismo")
        REFERENCES tipo_turismo ("TipIdTipoTurismo") ON DELETE RESTRICT,
    CONSTRAINT uq_zti_zona_tipo        UNIQUE ("ZtiIdZonaTuristica",
                                               "ZtiIdTipoTurismo")
);
 
-- ----------------------------------------------------------------------------
-- 10. RUTA_PEATONAL  (motor de rutas - RNF-04 circuito cerrado)
--
-- NOTA: "RutIdEstacionOrigen" es una FK propia y directa hacia estacion, en
-- vez de resolverse mediante zona_turistica.ZonIdEstacionCercana. Los campos
-- RutDistanciaKm/RutTiempoEstimadoMin/RutDificultad son el resultado de un
-- calculo (Haversine) hecho en un momento especifico con una estacion de
-- origen especifica (RutaPeatonalService, seccion 5.1). Si la estacion mas
-- cercana de la zona cambiara mas adelante, una ruta ya calculada no debe
-- cambiar de origen "en automatico" via JOIN, o sus valores de distancia y
-- tiempo quedarian inconsistentes con la estacion realmente usada al calcularlos.
-- ----------------------------------------------------------------------------
CREATE TABLE ruta_peatonal (
    "RutIdRuta"              INTEGER GENERATED ALWAYS AS IDENTITY,
    "RutNombre"              VARCHAR(100)   NOT NULL,
    "RutDescripcion"         VARCHAR(500)   NULL,
    "RutDistanciaKm"         NUMERIC(5,2)   NOT NULL,
    "RutTiempoEstimadoMin"   INTEGER        NOT NULL,
    "RutIdDificultad"        INTEGER        NOT NULL,
    "RutIdEstacionOrigen"    INTEGER        NOT NULL,
    "RutIdZonaDestino"       INTEGER        NOT NULL,
    "RutEsIdaVuelta"         BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_ruta_peatonal      PRIMARY KEY ("RutIdRuta"),
    CONSTRAINT fk_ruta_estacion      FOREIGN KEY ("RutIdEstacionOrigen")
        REFERENCES estacion ("EstIdEstacion") ON DELETE RESTRICT,
    CONSTRAINT fk_ruta_zona          FOREIGN KEY ("RutIdZonaDestino")
        REFERENCES zona_turistica ("ZonIdZona") ON DELETE RESTRICT,
    CONSTRAINT fk_ruta_dificultad    FOREIGN KEY ("RutIdDificultad")
        REFERENCES dificultad ("DifIdDificultad") ON DELETE RESTRICT,
    CONSTRAINT ck_ruta_ida_vuelta    CHECK ("RutEsIdaVuelta" = TRUE),
    CONSTRAINT ck_ruta_distancia     CHECK ("RutDistanciaKm" > 0),
    CONSTRAINT ck_ruta_tiempo        CHECK ("RutTiempoEstimadoMin" > 0),
    -- El circuito de ida y vuelta entre una estacion y una zona es unico: sin
    -- esta restriccion, dos consultas simultaneas de la misma ruta crean dos
    -- filas con los mismos valores calculados.
    CONSTRAINT uq_ruta_origen_zona   UNIQUE ("RutIdEstacionOrigen", "RutIdZonaDestino")
);
 
-- ----------------------------------------------------------------------------
-- 11. PREVISION_CLIMA  (integracion SENAMHI)
-- ----------------------------------------------------------------------------
CREATE TABLE prevision_clima (
    "CliIdClima"              INTEGER GENERATED ALWAYS AS IDENTITY,
    "CliFecha"                DATE           NOT NULL,
    "CliTemperaturaMinimaC"   NUMERIC(4,1)   NOT NULL,
    "CliTemperaturaMaximaC"   NUMERIC(4,1)   NOT NULL,
    "CliProbabilidadLluvia"   NUMERIC(4,1)   NOT NULL,
    "CliEstadoClima"          VARCHAR(30)    NOT NULL,
    "CliIdEstacion"           INTEGER        NOT NULL,
    CONSTRAINT pk_prevision_clima    PRIMARY KEY ("CliIdClima"),
    CONSTRAINT fk_clima_estacion     FOREIGN KEY ("CliIdEstacion")
        REFERENCES estacion ("EstIdEstacion") ON DELETE RESTRICT,
    CONSTRAINT ck_clima_lluvia       CHECK ("CliProbabilidadLluvia" BETWEEN 0 AND 100),
    CONSTRAINT ck_clima_temp_rango   CHECK ("CliTemperaturaMaximaC" >= "CliTemperaturaMinimaC"),
    CONSTRAINT uq_clima_est_fecha    UNIQUE ("CliIdEstacion", "CliFecha")
);
 
-- ----------------------------------------------------------------------------
-- 12. INFORME_PLANIFICACION  (modulo de informes)
-- ----------------------------------------------------------------------------
CREATE TABLE informe_planificacion (
    "InfIdInforme"       INTEGER GENERATED ALWAYS AS IDENTITY,
    "InfCodigo"          VARCHAR(15)    NOT NULL,
    "InfFechaEmision"    TIMESTAMP      NOT NULL DEFAULT NOW(),
    "InfFechaVisita"     DATE           NOT NULL,
    "InfIdUsuario"       INTEGER        NULL,
    "InfIdRuta"          INTEGER        NOT NULL,
    "InfTotalEstimado"   NUMERIC(7,2)   NOT NULL,
    CONSTRAINT pk_informe            PRIMARY KEY ("InfIdInforme"),
    CONSTRAINT uq_informe_codigo     UNIQUE ("InfCodigo"),
    CONSTRAINT fk_informe_usuario    FOREIGN KEY ("InfIdUsuario")
        REFERENCES usuario ("UsuIdUsuario") ON DELETE RESTRICT,
    CONSTRAINT fk_informe_ruta       FOREIGN KEY ("InfIdRuta")
        REFERENCES ruta_peatonal ("RutIdRuta") ON DELETE RESTRICT,
    CONSTRAINT ck_informe_total      CHECK ("InfTotalEstimado" >= 0)
);
 
-- ----------------------------------------------------------------------------
-- 13. INFORME_VISITANTE  (composicion del grupo que viaja)
--
-- El sistema es un asesor: no vende pasajes ni entradas. Por eso NO se modela
-- una tabla intermedia usuario <-> informe_planificacion; esa relacion N:M
-- representaria varias cuentas compartiendo un mismo informe, que no es el
-- caso de una familia -- una familia de tres no tiene tres cuentas, tiene un
-- turista que consulta por los tres.
--
-- Lo que si hace falta es la composicion del grupo, porque el precio depende
-- de la edad de cada acompanante. Cada fila agrupa a las personas de una
-- misma edad ("IviCantidad" personas de "IviEdad" anos), de modo que una
-- familia de dos adultos y un nino son dos filas y no tres.
--
-- Las categorias y los subtotales se guardan resueltos, no se recalculan al
-- leer: el informe es un documento historico y las tarifas cambian. Es el
-- mismo criterio con el que la seccion 6.3 justifica que RutaPeatonal guarde
-- su propia estacion de origen.
-- ----------------------------------------------------------------------------
CREATE TABLE informe_visitante (
    "IviIdVisitante"      INTEGER GENERATED ALWAYS AS IDENTITY,
    "IviIdInforme"        INTEGER        NOT NULL,
    "IviEdad"             SMALLINT       NOT NULL,
    "IviCantidad"         SMALLINT       NOT NULL DEFAULT 1,
    "IviIdCategoriaTren"  INTEGER        NULL,
    "IviIdCategoriaZona"  INTEGER        NOT NULL,
    "IviSubtotalTren"     NUMERIC(7,2)   NOT NULL DEFAULT 0,
    "IviSubtotalZona"     NUMERIC(7,2)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_informe_visitante   PRIMARY KEY ("IviIdVisitante"),
    CONSTRAINT fk_visitante_informe   FOREIGN KEY ("IviIdInforme")
        REFERENCES informe_planificacion ("InfIdInforme") ON DELETE CASCADE,
    CONSTRAINT fk_visitante_cat_tren  FOREIGN KEY ("IviIdCategoriaTren")
        REFERENCES categoria_visitante ("CatIdCategoria") ON DELETE RESTRICT,
    CONSTRAINT fk_visitante_cat_zona  FOREIGN KEY ("IviIdCategoriaZona")
        REFERENCES categoria_visitante ("CatIdCategoria") ON DELETE RESTRICT,
    CONSTRAINT ck_visitante_edad      CHECK ("IviEdad" BETWEEN 0 AND 120),
    CONSTRAINT ck_visitante_cantidad  CHECK ("IviCantidad" > 0),
    CONSTRAINT ck_visitante_sub_tren  CHECK ("IviSubtotalTren" >= 0),
    CONSTRAINT ck_visitante_sub_zona  CHECK ("IviSubtotalZona" >= 0),
    -- Una sola fila por edad dentro del informe: obliga a agrupar en
    -- "IviCantidad" en vez de repetir a los acompanantes de la misma edad.
    CONSTRAINT uq_visitante_informe_edad UNIQUE ("IviIdInforme", "IviEdad")
);
 
-- ----------------------------------------------------------------------------
-- 14. CONTROL_AFORO  (RF-16 / RNF-08 concurrencia)
-- ----------------------------------------------------------------------------
CREATE TABLE control_aforo (
    "AfoIdAforo"         INTEGER GENERATED ALWAYS AS IDENTITY,
    "AfoIdZona"          INTEGER   NOT NULL,
    "AfoFecha"           DATE      NOT NULL,
    -- Numero de PERSONAS ya confirmadas, no de informes: un informe de una
    -- familia de tres descuenta tres cupos del aforo diario de la zona.
    "AfoCupoUtilizado"   INTEGER   NOT NULL DEFAULT 0,
    CONSTRAINT pk_control_aforo      PRIMARY KEY ("AfoIdAforo"),
    CONSTRAINT fk_aforo_zona         FOREIGN KEY ("AfoIdZona")
        REFERENCES zona_turistica ("ZonIdZona") ON DELETE RESTRICT,
    CONSTRAINT uq_aforo_zona_fecha   UNIQUE ("AfoIdZona", "AfoFecha"),
    CONSTRAINT ck_aforo_cupo         CHECK ("AfoCupoUtilizado" >= 0)
);
 
-- ----------------------------------------------------------------------------
-- 15. AUDITORIA_LOG  (RNF-07 trazabilidad; sin FK fisica)
-- ----------------------------------------------------------------------------
CREATE TABLE auditoria_log (
    "AudIdLog"           INTEGER GENERATED ALWAYS AS IDENTITY,
    "AudFecha"           TIMESTAMP      NOT NULL DEFAULT NOW(),
    "AudUsuario"         VARCHAR(50)    NOT NULL,
    "AudOperacion"       VARCHAR(20)    NOT NULL,
    "AudTablaAfectada"   VARCHAR(50)    NOT NULL,
    "AudValorAnterior"   VARCHAR(500)   NULL,
    "AudValorNuevo"      VARCHAR(500)   NULL,
    CONSTRAINT pk_auditoria_log      PRIMARY KEY ("AudIdLog"),
    CONSTRAINT ck_auditoria_operacion CHECK ("AudOperacion" IN
        ('INSERT', 'UPDATE', 'DELETE', 'SYNC'))
);
 
-- ============================================================================
-- INDICES  (soporte al RNF-01: tiempo de respuesta menor a 2 segundos)
-- ============================================================================
CREATE INDEX idx_zona_estacion      ON zona_turistica    ("ZonIdEstacionCercana");
CREATE INDEX idx_zti_zona           ON zona_tipo_turismo ("ZtiIdZonaTuristica");
CREATE INDEX idx_zti_tipo           ON zona_tipo_turismo ("ZtiIdTipoTurismo");
CREATE INDEX idx_servicio_origen    ON servicio_tren     ("SerIdEstacionOrigen");
CREATE INDEX idx_servicio_destino   ON servicio_tren     ("SerIdEstacionDestino");
CREATE INDEX idx_ruta_estacion      ON ruta_peatonal     ("RutIdEstacionOrigen");
CREATE INDEX idx_ruta_zona          ON ruta_peatonal     ("RutIdZonaDestino");
CREATE INDEX idx_clima_estacion     ON prevision_clima   ("CliIdEstacion");
CREATE INDEX idx_clima_fecha        ON prevision_clima   ("CliFecha");
CREATE INDEX idx_auditoria_fecha    ON auditoria_log     ("AudFecha");
CREATE INDEX idx_aforo_zona_fecha   ON control_aforo     ("AfoIdZona", "AfoFecha");
CREATE INDEX idx_ruta_dificultad    ON ruta_peatonal     ("RutIdDificultad");
CREATE INDEX idx_visitante_informe  ON informe_visitante ("IviIdInforme");
CREATE INDEX idx_informe_usuario    ON informe_planificacion ("InfIdUsuario");
CREATE INDEX idx_informe_ruta       ON informe_planificacion ("InfIdRuta");