package com.turismo.repository;

import com.turismo.model.Estacion;
import com.turismo.model.ServicioTren;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-07 sobre H2 (perfil de pruebas): el selector de trenes del detalle de
 * ruta se alimenta de listarHaciaEstacion, y solo debe ofrecer servicios en
 * los que el turista pueda abordar, es decir, que salgan de una estacion
 * activa (RF-02). El mantenimiento admite registrar un servicio desde una
 * estacion inactiva, asi que el filtro tiene que estar en la consulta.
 */
@DataJpaTest
class ServicioTrenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ServicioTrenRepository servicioTrenRepository;

    private Estacion estacion(String codigo, String nombre, String estado) {
        Estacion estacion = new Estacion();
        estacion.setCodigo(codigo);
        estacion.setNombre(nombre);
        estacion.setLatitud(new BigDecimal("-13.154700"));
        estacion.setLongitud(new BigDecimal("-72.525000"));
        estacion.setCiudad("Cusco");
        estacion.setEstado(estado);
        return entityManager.persist(estacion);
    }

    private void servicio(Estacion origen, Estacion destino, LocalTime salida, int transito, String tarifa) {
        ServicioTren servicio = new ServicioTren();
        servicio.setEstacionOrigen(origen);
        servicio.setEstacionDestino(destino);
        servicio.setHorarioSalida(salida);
        servicio.setHorarioLlegada(salida.plusMinutes(transito));
        servicio.setTiempoTransitoMin(transito);
        servicio.setTarifa(new BigDecimal(tarifa));
        entityManager.persist(servicio);
    }

    @Test
    @DisplayName("Los servicios que salen de una estación inactiva no se ofrecen al turista")
    void listarHaciaEstacion_omiteLosServiciosQueSalenDeUnaEstacionInactiva() {
        Estacion machuPicchu = estacion("CUS-MAP", "Estacion Machu Picchu", "Activa");
        Estacion ollantaytambo = estacion("CUS-OLL", "Estacion Ollantaytambo", "Activa");
        Estacion poroy = estacion("CUS-POR", "Estacion Poroy", "Inactiva");
        servicio(ollantaytambo, machuPicchu, LocalTime.of(7, 45), 80, "280.00");
        servicio(ollantaytambo, machuPicchu, LocalTime.of(5, 7), 88, "210.00");
        servicio(poroy, machuPicchu, LocalTime.of(6, 40), 200, "360.00");
        // Sentido contrario: no llega a Machu Picchu, tampoco debe salir.
        servicio(machuPicchu, ollantaytambo, LocalTime.of(15, 35), 85, "280.00");
        entityManager.flush();
        entityManager.clear();

        List<ServicioTren> ofrecidos = servicioTrenRepository.listarHaciaEstacion(machuPicchu.getId());

        // Solo los que llegan a Machu Picchu desde una estacion activa, del
        // mas barato al mas caro.
        assertThat(ofrecidos)
                .extracting(s -> s.getEstacionOrigen().getCodigo(), ServicioTren::getTarifa)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("CUS-OLL", new BigDecimal("210.00")),
                        org.assertj.core.api.Assertions.tuple("CUS-OLL", new BigDecimal("280.00")));
    }
}
