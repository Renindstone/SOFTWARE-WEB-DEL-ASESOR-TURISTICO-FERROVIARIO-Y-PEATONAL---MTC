-- ============================================================================
-- Proyecto : Asesor Turistico Ferroviario y Peatonal - MTC
-- Curso    : Ingenieria de Software - UNU
-- Motor    : PostgreSQL 15
-- Script   : 02_datos.sql  (DML - datos de prueba / semilla)
--
-- Ejecutar DESPUES de 01_esquema.sql
--
-- NOTA 1: las llaves primarias son GENERATED ALWAYS AS IDENTITY, por lo que
--         NO se insertan valores de ID explicitos. Las llaves foraneas se
--         resuelven mediante subconsultas sobre los campos UNIQUE de cada
--         tabla, para que el script sea independiente del orden de los IDs.
--
-- NOTA 2: las estaciones y sus coordenadas corresponden a la red real de
--         PeruRail (corredores Cusco-Machu Picchu y Cusco-Puno-Arequipa).
--         Los horarios y tarifas son datos de prueba representativos
--         (simulacion del feed de PeruRail definida en el Sprint 0).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. ROL
-- ----------------------------------------------------------------------------
INSERT INTO rol ("RolNombreRol") VALUES
    ('ADMIN_MTC'),
    ('TRAVEL_GROUP_USER'),
    ('PERURAIL_ADMIN'),
    ('TURISTA_PUBLICO');

-- ----------------------------------------------------------------------------
-- 2. USUARIO
--
-- Contrasenias en texto plano (solo para pruebas), ya cifradas con BCrypt:
--   admin_mtc    -> Admin1234
--   travel_ana   -> Travel1234
--   rail_luis    -> Rail1234
--   turista_jose -> Turista1234
-- ----------------------------------------------------------------------------
INSERT INTO usuario ("UsuNombreUsuario", "UsuContrasenia", "UsuNombre",
                     "UsuApellidos", "UsuEmail", "UsuIdRol", "UsuEstado") VALUES
    ('admin_mtc',
     '$2b$10$uafTupUNhGEy/Q8LmUrkweIMSgPEXetALfskq0D14GNJeL8OXVx6a',
     'Carlos', 'Torres Ramirez', 'carlos.torres@mtc.gob.pe',
     (SELECT "RolIdRol" FROM rol WHERE "RolNombreRol" = 'ADMIN_MTC'), 'Activo'),
    ('travel_ana',
     '$2b$10$QQzjSUdbVrZIf6V17PO1WOI4ANoTGUG4WeNqiLO8C7j/NT6R.6Ryi',
     'Ana', 'Vargas Quispe', 'ana.vargas@travelgroup.com.pe',
     (SELECT "RolIdRol" FROM rol WHERE "RolNombreRol" = 'TRAVEL_GROUP_USER'), 'Activo'),
    ('rail_luis',
     '$2b$10$IQ.vbYQOzibT51gV2acYre/CyXj6EKjUUyyV8vK/WeZi3rMHg6Cqi',
     'Luis', 'Mamani Huaman', 'luis.mamani@perurail.com',
     (SELECT "RolIdRol" FROM rol WHERE "RolNombreRol" = 'PERURAIL_ADMIN'), 'Activo'),
    ('turista_jose',
     '$2b$10$GiYXMp5scEMkiv5reEIZ1OGqnF2WK.CD1.v1RlHz5BYhC6.pgnWZO',
     'Jose', 'Rojas Diaz', 'jose.rojas@correo.pe',
     (SELECT "RolIdRol" FROM rol WHERE "RolNombreRol" = 'TURISTA_PUBLICO'), 'Activo');

-- ----------------------------------------------------------------------------
-- 3. PREFERENCIA  (tabla parametrica - RNF-06)
-- ----------------------------------------------------------------------------
INSERT INTO preferencia ("PreNombre", "PreDescripcion") VALUES
    ('Historia/Cultura', 'Sitios arqueologicos, templos, museos y centros historicos'),
    ('Naturaleza',       'Paisajes, miradores, jardines, aguas termales y flora local'),
    ('Aventura',         'Rutas de ascenso, senderos exigentes y actividades al aire libre'),
    ('Gastronomia',      'Mercados tradicionales y zonas de comida local');

-- ----------------------------------------------------------------------------
-- 4. DIFICULTAD  (parametrica - RNF-06)
--
-- "DifDistanciaMaximaKm" se mide sobre el circuito completo de ida y vuelta y
-- reproduce los umbrales que hasta ahora estaban escritos en
-- RutaPeatonalService (hasta 3 km Baja, hasta 6 km Media, por encima Alta).
-- "DifVelocidadMinPorKm" recoge que un ascenso exigente se camina mas lento
-- que un paseo llano; antes se usaban 12 min/km para todos los casos.
-- ----------------------------------------------------------------------------
INSERT INTO dificultad ("DifNombre", "DifDescripcion", "DifOrden",
                        "DifDistanciaMaximaKm", "DifVelocidadMinPorKm") VALUES
    ('Baja',  'Recorrido llano o de pendiente suave, apto para cualquier visitante.',   1,  3.00, 12),
    ('Media', 'Tramos con pendiente o escalinatas; requiere calzado adecuado.',         2,  6.00, 18),
    ('Alta',  'Ascenso pronunciado o sendero largo; exige buena condicion fisica.',     3,  NULL, 25);

-- ----------------------------------------------------------------------------
-- 5. CATEGORIA_VISITANTE  (parametrica - tarifas por edad)
--
-- Ambito Tren: politica publicada por PeruRail. El infante de 0 a 2 anos viaja
-- gratis en brazos de un adulto y el nino de 3 a 11 paga la mitad de la tarifa
-- adulta; a partir de los 12 se paga tarifa completa.
--
-- Ambito Zona: politica de la tarifa del santuario. El corte de nino llega
-- hasta los 17 anos, no hasta los 11: por eso los rangos no pueden compartirse
-- con los del tren. El factor 0.6800 sale de la relacion entre la tarifa de
-- nino y la de adulto para visitantes nacionales y de la CAN.
--
-- La tarifa de estudiante no se modela aqui: depende de un carne vigente que
-- se acredita al ingresar, no de la edad.
-- ----------------------------------------------------------------------------
INSERT INTO categoria_visitante ("CatAmbito", "CatNombre", "CatEdadMinima",
                                 "CatEdadMaxima", "CatFactorPrecio", "CatDescripcion") VALUES
    ('Tren', 'Infante',  0,   2, 0.0000, 'De 0 a 2 anos. No paga pasaje; viaja en brazos de un adulto.'),
    ('Tren', 'Nino',     3,  11, 0.5000, 'De 3 a 11 anos. Paga el 50% de la tarifa adulta.'),
    ('Tren', 'Adulto',  12, NULL, 1.0000, 'Desde los 12 anos. Tarifa completa.'),
    ('Zona', 'Infante',  0,   2, 0.0000, 'De 0 a 2 anos. No paga entrada.'),
    ('Zona', 'Nino',     3,  17, 0.6800, 'De 3 a 17 anos. Tarifa reducida de ingreso.'),
    ('Zona', 'Adulto',  18, NULL, 1.0000, 'Desde los 18 anos. Tarifa completa de ingreso.');

-- ----------------------------------------------------------------------------
-- 6. ESTACION  (red real de PeruRail)
--
-- La estacion Poroy se registra como 'Inactiva': permite validar el RF-02
-- (excluir estaciones inactivas del selector) y el caso de prueba CN-02.
-- ----------------------------------------------------------------------------
INSERT INTO estacion ("EstCodigo", "EstNombre", "EstLatitud", "EstLongitud",
                      "EstAltitud", "EstCiudad", "EstEstado") VALUES
    ('CUS-SPD', 'Estacion San Pedro',            -13.522500, -71.982200, 3399.00, 'Cusco',           'Activa'),
    ('CUS-POR', 'Estacion Poroy',                        -13.474400, -72.042800, 3500.00, 'Cusco',           'Inactiva'),
    ('CUS-URU', 'Estacion Urubamba',                     -13.304900, -72.116300, 2871.00, 'Urubamba',        'Activa'),
    ('CUS-OLL', 'Estacion Ollantaytambo',                -13.258600, -72.265000, 2792.00, 'Ollantaytambo',   'Activa'),
    ('CUS-MAP', 'Estacion Machu Picchu', -13.154700, -72.525000, 2040.00, 'Aguas Calientes', 'Activa'),
    ('CUS-HID', 'Estacion Hidroelectrica',               -13.174700, -72.547800, 1850.00, 'Santa Teresa',    'Activa'),
    ('PUN-PUN', 'Estacion Puno',                         -15.840200, -70.021900, 3827.00, 'Puno',            'Activa'),
    ('AQP-AQP', 'Estacion Arequipa',                     -16.398900, -71.535000, 2335.00, 'Arequipa',        'Activa');

-- ----------------------------------------------------------------------------
-- 7. SERVICIO_TREN  (horarios y tarifas - simulacion del feed de PeruRail)
-- ----------------------------------------------------------------------------
INSERT INTO servicio_tren ("SerHorarioSalida", "SerHorarioLlegada",
                           "SerTiempoTransitoMin", "SerTarifa",
                           "SerIdEstacionOrigen", "SerIdEstacionDestino") VALUES
    -- Corredor Cusco (San Pedro) <-> Machu Picchu
    ('06:10', '09:54', 224, 210.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP')),
    ('14:55', '18:45', 230, 210.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD')),
    -- Corredor Ollantaytambo <-> Machu Picchu (servicio Expedition)
    ('05:07', '06:35',  88, 145.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP')),
    ('08:53', '10:22',  89, 145.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL')),
    -- Corredor Ollantaytambo <-> Machu Picchu (servicio Vistadome)
    ('07:45', '09:05',  80, 195.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP')),
    ('15:35', '17:00',  85, 195.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL')),
    -- Corredor Urubamba <-> Machu Picchu
    ('06:00', '08:23', 143, 165.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-URU'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP')),
    ('16:12', '18:40', 148, 165.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-URU')),
    -- Tramo Hidroelectrica <-> Machu Picchu
    ('08:30', '09:10',  40,  90.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-HID'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP')),
    ('14:30', '15:10',  40,  90.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-HID')),
    -- Corredor Cusco <-> Puno (Titicaca Train)
    ('08:00', '18:00', 600, 950.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'PUN-PUN')),
    ('08:00', '18:00', 600, 950.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'PUN-PUN'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD')),
    -- Corredor Puno <-> Arequipa
    ('21:00', '08:00', 660, 1200.00,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'PUN-PUN'),
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'AQP-AQP'));

-- ----------------------------------------------------------------------------
-- 8. ZONA_TURISTICA  (carga de Travel Group Peru)
--
-- Solo zonas alcanzables A PIE desde la estacion asociada (modelo del caso).
-- "ZonCupoMaximoDiario" se completa unicamente en las zonas que manejan
-- aforo controlado; en el resto queda NULL (RF-16).
-- ----------------------------------------------------------------------------
INSERT INTO zona_turistica ("ZonNombre", "ZonDescripcion",
                            "ZonLatitud", "ZonLongitud", "ZonIdEstacionCercana",
                            "ZonCostoAprox", "ZonCupoMaximoDiario", "ZonEstado") VALUES
    -- Desde Estacion San Pedro (Cusco)
    ('Mercado Central de San Pedro',
     'Mercado tradicional cusqueno con puestos de comida, jugos y artesania local.',
     -13.521172, -71.981959,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'), 0.00, NULL, 'Activa'),
    ('Plaza de Armas del Cusco',
     'Centro historico de la ciudad, rodeado por la Catedral y la Iglesia de la Compania.',
     -13.518684, -71.978276,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'), 0.00, NULL, 'Activa'),
    ('Templo del Qorikancha',
     'Antiguo templo inca dedicado al Sol, sobre el cual se edifico el convento de Santo Domingo.',
     -13.525268, -71.974377,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'), 15.00, NULL, 'Activa'),
    ('Barrio de San Blas',
     'Barrio de artesanos con calles empedradas, miradores y talleres tradicionales.',
     -13.515563, -71.973697,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'), 0.00, NULL, 'Activa'),
    ('Parque Arqueologico de Sacsayhuaman',
     'Complejo ceremonial inca de muros ciclopeos, con vista panoramica de la ciudad del Cusco.',
     -13.502130, -71.985894,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-SPD'), 70.00, 3000, 'Activa'),
    -- Desde Estacion Ollantaytambo
    ('Conjunto Arqueologico de Ollantaytambo',
     'Fortaleza y centro ceremonial inca con andenerias y el Templo del Sol.',
     -13.254466, -72.268563,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL'), 70.00, 2500, 'Activa'),
    ('Colcas de Pinkuylluna',
     'Antiguos depositos incas ubicados en la ladera del cerro, con vista al valle.',
     -13.252400, -72.259655,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL'), 0.00, NULL, 'Activa'),
    -- Desde Estacion Machu Picchu (Aguas Calientes)
    ('Llaqta de Machu Picchu',
     'Santuario historico inca declarado Patrimonio de la Humanidad por la UNESCO.',
     -13.193641, -72.548093,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'), 152.00, 4500, 'Activa'),
    ('Banos Termales de Aguas Calientes',
     'Pozas de aguas termales naturales en el centro del pueblo de Machu Picchu.',
     -13.147615, -72.526283,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'), 20.00, NULL, 'Activa'),
    ('Jardines de Mandor',
     'Reserva privada con senderos, catarata y avistamiento de aves y orquideas.',
     -13.149701, -72.554104,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'), 10.00, NULL, 'Activa'),
    ('Museo de Sitio Manuel Chavez Ballon',
     'Museo con piezas y hallazgos de las excavaciones del santuario de Machu Picchu.',
     -13.156189, -72.542481,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-MAP'), 22.00, NULL, 'Activa'),
    -- Desde Estacion Urubamba
    ('Plaza de Armas de Urubamba',
     'Plaza principal del valle sagrado, con iglesia colonial y mercado artesanal.',
     -13.295007, -72.116300,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-URU'), 0.00, NULL, 'Activa'),
    -- Desde Estacion Puno
    ('Mirador Kuntur Wasi',
     'Mirador en lo alto de la ciudad con vista panoramica del lago Titicaca.',
     -15.839102, -70.034938,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'PUN-PUN'), 5.00, NULL, 'Activa'),
    ('Puerto Lacustre de Puno',
     'Malecon y muelle turistico a orillas del lago Titicaca.',
     -15.840984, -70.012587,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'PUN-PUN'), 0.00, NULL, 'Activa'),
    -- Desde Estacion Arequipa
    ('Monasterio de Santa Catalina',
     'Ciudadela religiosa colonial con calles, patios y arquitectura en sillar.',
     -16.410414, -71.532884,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'AQP-AQP'), 45.00, NULL, 'Activa'),
    ('Mirador de Yanahuara',
     'Mirador de arcos de sillar con vista al volcan Misti.',
     -16.381153, -71.541733,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'AQP-AQP'), 0.00, NULL, 'Activa'),
    -- Zona dada de baja (permite validar el filtrado por estado)
    ('Sendero Antiguo del Rio Vilcanota',
     'Sendero cerrado temporalmente por trabajos de mantenimiento en la ribera.',
     -13.263767, -72.260546,
     (SELECT "EstIdEstacion" FROM estacion WHERE "EstCodigo" = 'CUS-OLL'), 0.00, NULL, 'Inactiva');

-- ----------------------------------------------------------------------------
-- 9. ZONA_PREFERENCIA  (relacion N:M)
--
-- Varias zonas combinan mas de una categoria a la vez: por ejemplo, el
-- Conjunto Arqueologico de Ollantaytambo es Historia/Cultura Y Naturaleza,
-- por lo que debe aparecer en las busquedas de ambas preferencias (CN-10).
-- ----------------------------------------------------------------------------
INSERT INTO zona_preferencia ("ZprIdZonaTuristica", "ZprIdPreferencia")
SELECT z."ZonIdZona", p."PreIdPreferencia"
FROM (VALUES
    ('Mercado Central de San Pedro',            'Gastronomia'),
    ('Mercado Central de San Pedro',            'Historia/Cultura'),
    ('Plaza de Armas del Cusco',                'Historia/Cultura'),
    ('Templo del Qorikancha',                   'Historia/Cultura'),
    ('Barrio de San Blas',                      'Historia/Cultura'),
    ('Barrio de San Blas',                      'Gastronomia'),
    ('Parque Arqueologico de Sacsayhuaman',     'Historia/Cultura'),
    ('Parque Arqueologico de Sacsayhuaman',     'Naturaleza'),
    ('Parque Arqueologico de Sacsayhuaman',     'Aventura'),
    ('Conjunto Arqueologico de Ollantaytambo',  'Historia/Cultura'),
    ('Conjunto Arqueologico de Ollantaytambo',  'Naturaleza'),
    ('Colcas de Pinkuylluna',                   'Historia/Cultura'),
    ('Colcas de Pinkuylluna',                   'Aventura'),
    ('Llaqta de Machu Picchu',                  'Historia/Cultura'),
    ('Llaqta de Machu Picchu',                  'Naturaleza'),
    ('Banos Termales de Aguas Calientes',       'Naturaleza'),
    ('Jardines de Mandor',                      'Naturaleza'),
    ('Jardines de Mandor',                      'Aventura'),
    ('Museo de Sitio Manuel Chavez Ballon',     'Historia/Cultura'),
    ('Plaza de Armas de Urubamba',              'Historia/Cultura'),
    ('Mirador Kuntur Wasi',                     'Naturaleza'),
    ('Mirador Kuntur Wasi',                     'Aventura'),
    ('Puerto Lacustre de Puno',                 'Naturaleza'),
    ('Monasterio de Santa Catalina',            'Historia/Cultura'),
    ('Mirador de Yanahuara',                    'Historia/Cultura'),
    ('Mirador de Yanahuara',                    'Naturaleza'),
    ('Sendero Antiguo del Rio Vilcanota',       'Naturaleza')
) AS v(zona, preferencia)
JOIN zona_turistica z ON z."ZonNombre"  = v.zona
JOIN preferencia   p ON p."PreNombre"  = v.preferencia;

-- ----------------------------------------------------------------------------
-- 10. RUTA_PEATONAL  (circuitos de ida y vuelta - RNF-04)
--
-- "RutDistanciaKm" corresponde al recorrido TOTAL (ida + vuelta), es decir,
-- el doble de la distancia estacion -> zona, segun el calculo definido en
-- RutaPeatonalService (formula de Haversine x 2).
-- ----------------------------------------------------------------------------
INSERT INTO ruta_peatonal ("RutNombre", "RutDescripcion", "RutDistanciaKm", "RutTiempoEstimadoMin",
                           "RutIdDificultad", "RutIdEstacionOrigen",
                           "RutIdZonaDestino", "RutEsIdaVuelta")
SELECT v.nombre, v.descripcion, v.km, v.minutos, d."DifIdDificultad",
       z."ZonIdEstacionCercana", z."ZonIdZona", TRUE
FROM (VALUES
    ('Circuito San Pedro - Mercado Central',
     'Paseo corto y llano por calles del centro historico hasta el mercado.', 0.30,   8, 'Baja',  'Mercado Central de San Pedro'),
    ('Circuito San Pedro - Plaza de Armas',
     'Caminata urbana llana por calles empedradas del centro del Cusco.', 1.20,  25, 'Baja',  'Plaza de Armas del Cusco'),
    ('Circuito San Pedro - Qorikancha',
     'Recorrido urbano llano pasando por calles coloniales hasta el templo inca.', 1.80,  35, 'Baja',  'Templo del Qorikancha'),
    ('Circuito San Pedro - San Blas',
     'Ascenso moderado por calles empedradas y escalinatas del barrio de artesanos.', 2.40,  55, 'Media', 'Barrio de San Blas'),
    ('Circuito San Pedro - Sacsayhuaman',
     'Ascenso pronunciado por sendero y escalinatas hasta el complejo ceremonial, con vista panoramica de la ciudad.', 4.60, 130, 'Alta',  'Parque Arqueologico de Sacsayhuaman'),
    ('Circuito Ollantaytambo - Fortaleza',
     'Caminata corta y llana desde la estacion hasta el ingreso de la fortaleza inca.', 1.20,  30, 'Baja',  'Conjunto Arqueologico de Ollantaytambo'),
    ('Circuito Ollantaytambo - Pinkuylluna',
     'Ascenso exigente por sendero de tierra en la ladera del cerro, sin baranda.', 1.80,  75, 'Alta',  'Colcas de Pinkuylluna'),
    ('Circuito Aguas Calientes - Llaqta a pie',
     'Ascenso largo y exigente por el sendero de escalinatas hacia el santuario de Machu Picchu (alternativa al bus).', 10.00, 240, 'Alta',  'Llaqta de Machu Picchu'),
    ('Circuito Aguas Calientes - Banos Termales',
     'Paseo llano por el pueblo hasta las pozas de aguas termales.', 1.60,  35, 'Baja',  'Banos Termales de Aguas Calientes'),
    ('Circuito Aguas Calientes - Mandor',
     'Caminata por sendero de tierra junto a la via ferrea hasta la reserva y su catarata.', 6.40, 130, 'Media', 'Jardines de Mandor'),
    ('Circuito Aguas Calientes - Museo de Sitio',
     'Recorrido llano junto al rio Urubamba hasta el museo de sitio.', 3.80,  90, 'Media', 'Museo de Sitio Manuel Chavez Ballon'),
    ('Circuito Urubamba - Plaza de Armas',
     'Paseo corto y llano por el centro del pueblo.', 2.20,  40, 'Baja',  'Plaza de Armas de Urubamba'),
    ('Circuito Puno - Mirador Kuntur Wasi',
     'Ascenso pronunciado por escalinatas hasta el mirador con vista al lago Titicaca.', 2.80,  70, 'Alta',  'Mirador Kuntur Wasi'),
    ('Circuito Puno - Puerto Lacustre',
     'Caminata llana por el malecon hasta el muelle turistico.', 2.00,  30, 'Baja',  'Puerto Lacustre de Puno'),
    ('Circuito Arequipa - Santa Catalina',
     'Paseo llano por el centro historico en sillar hasta el monasterio.', 2.60,  45, 'Baja',  'Monasterio de Santa Catalina'),
    ('Circuito Arequipa - Mirador de Yanahuara',
     'Caminata moderada por calles empedradas hasta el mirador con vista al volcan Misti.', 4.20,  80, 'Media', 'Mirador de Yanahuara')
) AS v(nombre, descripcion, km, minutos, dificultad, zona)
JOIN zona_turistica z ON z."ZonNombre" = v.zona
JOIN dificultad     d ON d."DifNombre" = v.dificultad;

-- ----------------------------------------------------------------------------
-- 11. PREVISION_CLIMA  (simulacion del feed diario del SENAMHI)
-- ----------------------------------------------------------------------------
INSERT INTO prevision_clima ("CliFecha", "CliTemperaturaMinimaC", "CliTemperaturaMaximaC",
                             "CliProbabilidadLluvia", "CliEstadoClima", "CliIdEstacion")
SELECT v.fecha::DATE, v.tmin, v.tmax, v.lluvia, v.estado,
       e."EstIdEstacion"
FROM (VALUES
    -- fecha,        tmin, tmax, lluvia, estado,                  estacion
    ('2026-09-01',   4.0, 18.5, 10.0, 'Soleado',             'CUS-SPD'),
    ('2026-09-02',   3.5, 17.2, 25.0, 'Parcialmente nublado','CUS-SPD'),
    ('2026-09-03',   5.0, 16.8, 40.0, 'Nublado',             'CUS-SPD'),
    ('2026-09-01',   5.5, 20.1, 15.0, 'Soleado',             'CUS-OLL'),
    ('2026-09-02',   5.0, 19.4, 30.0, 'Parcialmente nublado','CUS-OLL'),
    ('2026-09-03',   7.0, 18.0, 55.0, 'Lluvia ligera',       'CUS-OLL'),
    ('2026-09-01',  14.0, 23.6, 35.0, 'Parcialmente nublado','CUS-MAP'),
    ('2026-09-02',  15.0, 22.9, 60.0, 'Lluvia ligera',       'CUS-MAP'),
    ('2026-09-03',  16.5, 21.5, 80.0, 'Lluvioso',            'CUS-MAP'),
    ('2026-09-01',   6.0, 21.0, 20.0, 'Soleado',             'CUS-URU'),
    ('2026-09-02',   6.5, 20.3, 35.0, 'Parcialmente nublado','CUS-URU'),
    ('2026-09-01',  -2.0, 14.2, 12.0, 'Soleado',             'PUN-PUN'),
    ('2026-09-02',  -1.5, 13.8, 22.0, 'Parcialmente nublado','PUN-PUN'),
    ('2026-09-01',   8.0, 22.4,  5.0, 'Soleado',             'AQP-AQP'),
    ('2026-09-02',   8.5, 22.0,  8.0, 'Soleado',             'AQP-AQP')
) AS v(fecha, tmin, tmax, lluvia, estado, codigo)
JOIN estacion e ON e."EstCodigo" = v.codigo;

-- ----------------------------------------------------------------------------
-- 12. CONTROL_AFORO  (RF-16)
--
-- La Llaqta de Machu Picchu queda con el aforo COMPLETO para el 01/09/2026
-- (4500 de 4500), lo que permite ejecutar directamente el caso de prueba
-- CN-09 (rechazo de informe por aforo agotado).
-- ----------------------------------------------------------------------------
INSERT INTO control_aforo ("AfoIdZona", "AfoFecha", "AfoCupoUtilizado")
SELECT z."ZonIdZona", v.fecha::DATE, v.cupo
FROM (VALUES
    ('Llaqta de Machu Picchu',                 '2026-09-01', 4500),
    ('Llaqta de Machu Picchu',                 '2026-09-02', 1820),
    ('Llaqta de Machu Picchu',                 '2026-09-03',  640),
    ('Conjunto Arqueologico de Ollantaytambo', '2026-09-01',  320),
    ('Conjunto Arqueologico de Ollantaytambo', '2026-09-02',  145),
    ('Parque Arqueologico de Sacsayhuaman',    '2026-09-01',  980)
) AS v(zona, fecha, cupo)
JOIN zona_turistica z ON z."ZonNombre" = v.zona;

-- ----------------------------------------------------------------------------
-- 13. INFORME_PLANIFICACION  (RF-08)
--
-- InfTotalEstimado = tarifa del tren + costo aproximado de la zona.
-- El informe INF-0003 se genera sin usuario autenticado (consulta anonima).
-- ----------------------------------------------------------------------------
-- INF-0001 lo genera una familia de tres (ver informe_visitante): su total ya
-- no es el de una persona, sino la suma de los subtotales del grupo.
INSERT INTO informe_planificacion ("InfCodigo", "InfFechaVisita", "InfIdUsuario",
                                   "InfIdRuta", "InfTotalEstimado") VALUES
    ('INF-0001', '2026-09-02',
     (SELECT "UsuIdUsuario" FROM usuario WHERE "UsuNombreUsuario" = 'turista_jose'),
     (SELECT "RutIdRuta" FROM ruta_peatonal WHERE "RutNombre" = 'Circuito Ollantaytambo - Fortaleza'),
     550.10),
    ('INF-0002', '2026-09-03',
     (SELECT "UsuIdUsuario" FROM usuario WHERE "UsuNombreUsuario" = 'turista_jose'),
     (SELECT "RutIdRuta" FROM ruta_peatonal WHERE "RutNombre" = 'Circuito Aguas Calientes - Banos Termales'),
     165.00),
    ('INF-0003', '2026-09-02', NULL,
     (SELECT "RutIdRuta" FROM ruta_peatonal WHERE "RutNombre" = 'Circuito San Pedro - Sacsayhuaman'),
     70.00);

-- ----------------------------------------------------------------------------
-- 14. INFORME_VISITANTE  (composicion del grupo)
--
-- INF-0001 es el caso de la familia: dos adultos y un nino de 8 anos. Sirve
-- para comprobar que la edad cambia el precio en los dos ambitos a la vez y
-- con cortes distintos -- un chico de 8 es "Nino" tanto en el tren como en la
-- zona, pero uno de 15 seria "Adulto" en el tren y "Nino" en la zona.
--
-- Subtotales de INF-0001 (tarifa de tren 145.00, ingreso a la zona 70.00):
--   2 adultos -> tren 145.00 x 1.0000 x 2 = 290.00 ; zona 70.00 x 1.0000 x 2 = 140.00
--   1 nino    -> tren 145.00 x 0.5000     =  72.50 ; zona 70.00 x 0.6800     =  47.60
--   Total del informe = 550.10
--
-- INF-0003 se emitio sin tren, por eso su categoria de tren queda en NULL.
-- ----------------------------------------------------------------------------
INSERT INTO informe_visitante ("IviIdInforme", "IviEdad", "IviCantidad",
                               "IviIdCategoriaTren", "IviIdCategoriaZona",
                               "IviSubtotalTren", "IviSubtotalZona")
SELECT i."InfIdInforme", v.edad, v.cantidad,
       ct."CatIdCategoria", cz."CatIdCategoria",
       v.subtotal_tren, v.subtotal_zona
FROM (VALUES
    -- informe,     edad, cantidad, cat_tren,  cat_zona, sub_tren, sub_zona
    ('INF-0001', 35,  2, 'Adulto', 'Adulto', 290.00, 140.00),
    ('INF-0001',  8,  1, 'Nino',   'Nino',    72.50,  47.60),
    ('INF-0002', 35,  1, 'Adulto', 'Adulto', 145.00,  20.00),
    ('INF-0003', 28,  1,  NULL,    'Adulto',   0.00,  70.00)
) AS v(codigo, edad, cantidad, cat_tren, cat_zona, subtotal_tren, subtotal_zona)
JOIN informe_planificacion i ON i."InfCodigo" = v.codigo
LEFT JOIN categoria_visitante ct ON ct."CatAmbito" = 'Tren' AND ct."CatNombre" = v.cat_tren
JOIN      categoria_visitante cz ON cz."CatAmbito" = 'Zona' AND cz."CatNombre" = v.cat_zona;

-- ----------------------------------------------------------------------------
-- 15. AUDITORIA_LOG  (RNF-07)
-- ----------------------------------------------------------------------------
INSERT INTO auditoria_log ("AudUsuario", "AudOperacion", "AudTablaAfectada",
                           "AudValorAnterior", "AudValorNuevo") VALUES
    ('SISTEMA',    'SYNC',   'estacion',
     NULL, 'Sincronizacion PeruRail: 8 estaciones actualizadas'),
    ('SISTEMA',    'SYNC',   'servicio_tren',
     NULL, 'Sincronizacion PeruRail: 13 servicios actualizados'),
    ('SISTEMA',    'SYNC',   'prevision_clima',
     NULL, 'Sincronizacion SENAMHI: 15 previsiones actualizadas'),
    ('travel_ana', 'INSERT', 'zona_turistica',
     NULL, 'ZonNombre=Jardines de Mandor; ZonIdEstacionCercana=CUS-MAP'),
    ('travel_ana', 'UPDATE', 'zona_turistica',
     'ZonCostoAprox=8.00', 'ZonCostoAprox=10.00'),
    ('rail_luis',  'UPDATE', 'servicio_tren',
     'SerTarifa=140.00', 'SerTarifa=145.00'),
    ('admin_mtc',  'UPDATE', 'estacion',
     'EstEstado=Activa', 'EstEstado=Inactiva (Estacion Poroy)');

-- ============================================================================
-- FIN DEL SCRIPT DE DATOS DE PRUEBA
-- ============================================================================
