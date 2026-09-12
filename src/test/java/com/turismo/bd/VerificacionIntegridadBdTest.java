package com.turismo.bd;

import com.turismo.model.CategoriaVisitante;
import com.turismo.util.HaversineCalculator;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integridad del modelo de datos contra PostgreSQL real (seccion 8).
 *
 * El esquema define dos verificadores en init/03_consultas.sql, fn_verificar_categorias()
 * y fn_verificar_rutas(), pensados para ejecutarse a mano con un SELECT. Al no correrlos
 * nadie, los dos desajustes que vigilan solo se descubren en produccion: una edad sin
 * tarifa hace fallar la emision del informe, y una RutDistanciaKm obsoleta se le muestra
 * al turista sin que nada proteste. Esta clase los ejecuta en cada build.
 *
 * A diferencia del resto de la suite, esta prueba NO usa H2: los verificadores son plpgsql
 * y H2 no puede ejecutarlos. Levanta un PostgreSQL 15 con los mismos scripts de init que
 * monta el docker-compose, asi que valida los scripts reales y no una copia.
 */
@Testcontainers
class VerificacionIntegridadBdTest {

    /** Tolerancia con la que fn_verificar_rutas() compara la distancia guardada. */
    private static final String TOLERANCIA_RUTAS_KM = "0.05";

    private static final Path DIRECTORIO_INIT = localizarDirectorioInit();

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:15")
                    .withDatabaseName("bd_asesor_turistico")
                    .withUsername("admin_mtc")
                    .withPassword("mtc2026")
                    // Mismo mecanismo que el docker-compose: el entrypoint de la imagen
                    // ejecuta en orden alfabetico lo que encuentre en este directorio, de
                    // modo que 01_esquema, 02_datos y 03_consultas corren tal cual.
                    .withCopyFileToContainer(MountableFile.forHostPath(DIRECTORIO_INIT),
                                             "/docker-entrypoint-initdb.d/");

    private static Path localizarDirectorioInit() {
        Path init = Path.of("init").toAbsolutePath().normalize();
        if (!Files.isDirectory(init)) {
            throw new IllegalStateException(
                    "No se encontro el directorio init/ en " + init
                            + ". Esta prueba debe ejecutarse desde la raiz del modulo app-mtc.");
        }
        return init;
    }

    private static Connection abrirConexion() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /**
     * Guarda del resto de la clase. Tanto fn_verificar_categorias() como
     * fn_verificar_rutas() devuelven cero filas sobre una base vacia, asi que si los
     * scripts de init no se hubieran ejecutado las demas pruebas pasarian sin comprobar
     * nada. Exigir la funcion (03), las tablas (01) y las filas (02) a la vez prueba que
     * el entrypoint corrio los tres en orden.
     */
    @Test
    void losTresScriptsDeInitSeEjecutaronEnOrden() throws SQLException {
        Map<String, Long> conteos = new LinkedHashMap<>();
        try (Connection cx = abrirConexion();
             Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery("SELECT tabla, registros FROM fn_resumen_bd()")) {
            while (rs.next()) {
                conteos.put(rs.getString("tabla"), rs.getLong("registros"));
            }
        }

        assertThat(conteos)
                .describedAs("fn_resumen_bd() vive en init/03_consultas.sql: si falta, el "
                        + "directorio init/ no llego al contenedor")
                .containsKeys("categoria_visitante", "estacion", "zona_turistica", "ruta_peatonal");
        assertThat(conteos.get("categoria_visitante")).isPositive();
        assertThat(conteos.get("estacion")).isPositive();
        assertThat(conteos.get("zona_turistica")).isPositive();
        assertThat(conteos.get("ruta_peatonal")).isPositive();
    }

    /**
     * RF-19. La restriccion EXCLUDE de categoria_visitante impide que dos tramos de edad
     * se solapen, pero no que quede un hueco entre ellos: basta bajar el tope de nino sin
     * bajar el minimo de adulto. Con un hueco, TarifaService.categoriaPorEdad lanza
     * TarifaInvalidaException al generar el informe, delante del turista.
     */
    @Test
    void ningunaEdadSeQuedaSinCategoriaTarifaria() throws SQLException {
        List<String> huecos = new ArrayList<>();
        try (Connection cx = abrirConexion();
             Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT ambito, edad, problema FROM fn_verificar_categorias()")) {
            while (rs.next()) {
                huecos.add(rs.getString("ambito") + " / edad " + rs.getInt("edad")
                        + ": " + rs.getString("problema"));
            }
        }

        assertThat(huecos)
                .describedAs("Edades sin tarifa configurada en init/02_datos.sql")
                .isEmpty();
    }

    /**
     * RutDistanciaKm es un valor persistido, no derivado: editar las coordenadas de una
     * zona o de una estacion lo deja obsoleto en silencio. fn_verificar_rutas() recalcula
     * el circuito y marca REVISAR cuando la diferencia supera la tolerancia.
     */
    @Test
    void lasDistanciasGuardadasSiguenCoincidiendoConLasCoordenadas() throws SQLException {
        List<String> desalineadas = new ArrayList<>();
        try (Connection cx = abrirConexion();
             Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT ruta, km_registrados, km_haversine, diferencia_km "
                             + "FROM fn_verificar_rutas() WHERE estado <> 'OK'")) {
            while (rs.next()) {
                desalineadas.add(rs.getString("ruta")
                        + ": RutDistanciaKm=" + rs.getBigDecimal("km_registrados")
                        + " vs Haversine=" + rs.getBigDecimal("km_haversine")
                        + " (diferencia " + rs.getBigDecimal("diferencia_km") + " km)");
            }
        }

        assertThat(desalineadas)
                .describedAs("Rutas cuya distancia guardada se aparto mas de "
                        + TOLERANCIA_RUTAS_KM + " km de sus coordenadas")
                .isEmpty();
    }

    /**
     * La formula de Haversine esta escrita dos veces, en HaversineCalculator y en
     * fn_distancia_haversine, sin ninguna llamada que las una: HaversineCalculatorTest
     * fija a mano los valores esperados y por eso no puede detectar que una de las dos
     * cambie. Aqui se comparan las dos implementaciones sobre las coordenadas reales.
     */
    @Test
    void elHaversineDeJavaCoincideConElDePostgres() throws SQLException {
        String consulta = """
                SELECT r."RutNombre" AS ruta,
                       e."EstLatitud" AS est_lat, e."EstLongitud" AS est_lon,
                       z."ZonLatitud" AS zon_lat, z."ZonLongitud" AS zon_lon,
                       fn_distancia_haversine(e."EstLatitud", e."EstLongitud",
                                              z."ZonLatitud", z."ZonLongitud") AS km_sql
                FROM ruta_peatonal r
                JOIN estacion e       ON e."EstIdEstacion" = r."RutIdEstacionOrigen"
                JOIN zona_turistica z ON z."ZonIdZona"     = r."RutIdZonaDestino"
                """;

        List<String> discrepancias = new ArrayList<>();
        int comparadas = 0;
        try (Connection cx = abrirConexion();
             Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery(consulta)) {
            while (rs.next()) {
                comparadas++;
                BigDecimal kmJava = HaversineCalculator.calcularDistanciaKm(
                        rs.getBigDecimal("est_lat"), rs.getBigDecimal("est_lon"),
                        rs.getBigDecimal("zon_lat"), rs.getBigDecimal("zon_lon"));
                BigDecimal kmSql = rs.getBigDecimal("km_sql");
                if (kmJava.compareTo(kmSql) != 0) {
                    discrepancias.add(rs.getString("ruta")
                            + ": Java=" + kmJava + " km, PostgreSQL=" + kmSql + " km");
                }
            }
        }

        assertThat(comparadas)
                .describedAs("Sin rutas sembradas la comparacion no probaria nada")
                .isPositive();
        assertThat(discrepancias)
                .describedAs("HaversineCalculator y fn_distancia_haversine dejaron de "
                        + "devolver lo mismo sobre los mismos datos")
                .isEmpty();
    }

    /**
     * Los cortes de edad no coinciden entre proveedores: a los 15 anos se paga adulto en
     * el tren y nino en la zona. InformeServiceTest cubre esa regla, pero simulando el
     * repositorio con tramos escritos en la propia prueba, asi que sigue en verde aunque
     * init/02_datos.sql cambie. Esta comprobacion mira los datos que se cargan de verdad.
     */
    @Test
    void aLosQuinceSePagaAdultoEnElTrenYNinoEnLaZona() throws SQLException {
        assertThat(categoriaPorEdad(CategoriaVisitante.AMBITO_TREN, 15)).isEqualTo("Adulto");
        assertThat(categoriaPorEdad(CategoriaVisitante.AMBITO_ZONA, 15)).isEqualTo("Nino");
    }

    private static String categoriaPorEdad(String ambito, int edad) throws SQLException {
        try (Connection cx = abrirConexion();
             PreparedStatement ps = cx.prepareStatement(
                     "SELECT categoria FROM fn_categoria_por_edad(?, ?)")) {
            ps.setString(1, ambito);
            ps.setInt(2, edad);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .describedAs("Sin categoria para la edad %d en el ambito %s", edad, ambito)
                        .isTrue();
                return rs.getString("categoria");
            }
        }
    }
}
