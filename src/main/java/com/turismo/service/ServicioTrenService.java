package com.turismo.service;

import com.turismo.exception.ServicioTrenInvalidoException;
import com.turismo.integration.perurail.PeruRailClient;
import com.turismo.model.Estacion;
import com.turismo.model.ServicioTren;
import com.turismo.repository.ServicioTrenRepository;
import com.turismo.util.HaversineCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * RF-12: mantenimiento de horarios y precios de los servicios de tren
 * (administrador de PeruRail/MTC). Respalda la vista admin/servicios-tren.html
 * y deja traza de cada cambio en AuditoriaLog (RF-15/RNF-07).
 */
@Service
public class ServicioTrenService {

    private static final String TABLA_AUDITADA = "servicio_tren";

    /** Ver validarVelocidadFisica; el formulario admin/servicios-tren usa los mismos valores. */
    static final double FACTOR_VIA_FERREA = 1.25;
    static final double VELOCIDAD_MAXIMA_KMH = 80.0;

    private final ServicioTrenRepository servicioTrenRepository;
    private final PeruRailClient peruRailClient;
    private final AuditoriaService auditoriaService;

    public ServicioTrenService(ServicioTrenRepository servicioTrenRepository,
                                PeruRailClient peruRailClient,
                                AuditoriaService auditoriaService) {
        this.servicioTrenRepository = servicioTrenRepository;
        this.peruRailClient = peruRailClient;
        this.auditoriaService = auditoriaService;
    }

    public List<ServicioTren> listarPorEstacionOrigen(Integer idEstacionOrigen) {
        return servicioTrenRepository.findByEstacionOrigen_Id(idEstacionOrigen);
    }

    /** RF-07: servicios que llegan a la estacion de partida, ordenados por tarifa. */
    public List<ServicioTren> listarHaciaEstacion(Integer idEstacionDestino) {
        return servicioTrenRepository.listarHaciaEstacion(idEstacionDestino);
    }

    public List<ServicioTren> listarTodos() {
        return servicioTrenRepository.listarConEstaciones();
    }

    public Optional<ServicioTren> buscarPorId(Integer id) {
        return servicioTrenRepository.buscarConEstaciones(id);
    }

    /** CB-05/CB-06: valida la tarifa (PeruRailClient.validarTarifaPeruRail) antes de persistir. */
    @Transactional
    public ServicioTren guardar(ServicioTren servicioTren, String usuario) {
        peruRailClient.validarTarifaPeruRail(servicioTren.getTarifa());
        validarHorasDistintas(servicioTren);
        completarTiempoTransito(servicioTren);
        validarTramo(servicioTren);
        validarHorarios(servicioTren);
        validarVelocidadFisica(servicioTren);

        boolean esAlta = servicioTren.getId() == null;
        String valorAnterior = esAlta ? null
                : servicioTrenRepository.findById(servicioTren.getId()).map(this::describir).orElse(null);

        ServicioTren guardado = servicioTrenRepository.save(servicioTren);

        auditoriaService.registrarAuditoria(usuario, esAlta ? "INSERT" : "UPDATE", TABLA_AUDITADA,
                valorAnterior, describir(guardado));

        return guardado;
    }

    /**
     * Salida y llegada iguales no describen ningun viaje: completarTiempoTransito
     * las interpretaria como un servicio de 24 horas exactas (cruce de
     * medianoche), que es casi con seguridad un error al teclear.
     */
    private void validarHorasDistintas(ServicioTren servicioTren) {
        LocalTime salida = servicioTren.getHorarioSalida();
        LocalTime llegada = servicioTren.getHorarioLlegada();
        if (salida != null && salida.equals(llegada)) {
            throw new ServicioTrenInvalidoException(
                    "La hora de llegada debe ser distinta de la hora de salida");
        }
    }

    /**
     * Deriva el tiempo de transito de los dos horarios cuando no viene dado.
     *
     * El formulario de admin ya lo calcula en el navegador y lo envia en un
     * campo de solo lectura, pero esta es la unica garantia para quien llame
     * al servicio sin pasar por esa pantalla: la sincronizacion con PeruRail o
     * una prueba. Deriva en vez de rechazar porque el dato es redundante, no
     * falta informacion.
     *
     * Una diferencia nula o negativa significa que el servicio cruza la
     * medianoche: el tramo Puno - Arequipa sale 21:00 y llega 08:00, que son
     * 660 minutos y no -780.
     */
    private void completarTiempoTransito(ServicioTren servicioTren) {
        Integer transito = servicioTren.getTiempoTransitoMin();
        if (transito != null && transito > 0) {
            return;
        }

        LocalTime salida = servicioTren.getHorarioSalida();
        LocalTime llegada = servicioTren.getHorarioLlegada();
        if (salida == null || llegada == null) {
            return;
        }

        long minutos = ChronoUnit.MINUTES.between(salida, llegada);
        if (minutos <= 0) {
            minutos += 24 * 60;
        }
        servicioTren.setTiempoTransitoMin((int) minutos);
    }

    /**
     * Un tramo debe unir dos estaciones distintas. La base no lo impide y sin
     * esta comprobacion se podria dar de alta un servicio que sale y llega a
     * la misma estacion, que ademas apareceria despues en listarHaciaEstacion.
     */
    private void validarTramo(ServicioTren servicioTren) {
        Estacion origen = servicioTren.getEstacionOrigen();
        Estacion destino = servicioTren.getEstacionDestino();
        if (origen != null && destino != null && Objects.equals(origen.getId(), destino.getId())) {
            throw new ServicioTrenInvalidoException(
                    "La estación de destino debe ser distinta de la de origen");
        }
    }

    /**
     * El horario de llegada debe ser exactamente la salida mas el tiempo de
     * transito. Se compara sobre LocalTime.plusMinutes, que da la vuelta al
     * reloj por si solo, de modo que los servicios nocturnos siguen siendo
     * validos: el tramo Puno - Arequipa sale 21:00, viaja 660 minutos y llega
     * a las 08:00 del dia siguiente. Comparar "llegada posterior a salida"
     * habria rechazado ese servicio.
     */
    private void validarHorarios(ServicioTren servicioTren) {
        LocalTime salida = servicioTren.getHorarioSalida();
        LocalTime llegada = servicioTren.getHorarioLlegada();
        Integer transito = servicioTren.getTiempoTransitoMin();
        if (salida == null || llegada == null || transito == null) {
            return;
        }
        LocalTime esperada = salida.plusMinutes(transito);
        if (!esperada.equals(llegada)) {
            throw new ServicioTrenInvalidoException(
                    "El horario de llegada no concuerda con la salida más el tiempo de tránsito: "
                            + "con salida " + salida + " y " + transito + " minutos se llegaría a las "
                            + esperada + ", no a las " + llegada);
        }
    }

    /**
     * Una vez conocido el tiempo de transito, comprueba que la velocidad media
     * que implica el horario sea alcanzable en la red: un horario coherente
     * consigo mismo (llegada = salida + transito) puede seguir siendo
     * imposible, como Cusco - Machu Picchu en 20 minutos.
     *
     * La distancia se estima con Haversine (la misma clase que usa la ruta
     * peatonal) multiplicada por 1,25 para aproximar la longitud de la via,
     * que sigue curvas de nivel y no va en linea recta. Es una cota, no un
     * horario: los servicios de la carga inicial implican entre 20 y 42 km/h
     * de media segun el corredor, asi que 80 km/h deja margen de sobra sin
     * aceptar horarios que ningun tren de la red cumple. El formulario aplica
     * las mismas constantes para avisar antes de enviar.
     */
    private void validarVelocidadFisica(ServicioTren servicioTren) {
        Estacion origen = servicioTren.getEstacionOrigen();
        Estacion destino = servicioTren.getEstacionDestino();
        Integer transito = servicioTren.getTiempoTransitoMin();

        if (origen == null || destino == null || transito == null || transito <= 0) {
            return;
        }
        if (origen.getLatitud() == null || origen.getLongitud() == null
                || destino.getLatitud() == null || destino.getLongitud() == null) {
            return;
        }

        BigDecimal kmRecta = HaversineCalculator.calcularDistanciaKm(
                origen.getLatitud(), origen.getLongitud(),
                destino.getLatitud(), destino.getLongitud());

        double kmVia = kmRecta.doubleValue() * FACTOR_VIA_FERREA;
        double velocidad = kmVia / (transito / 60.0);

        if (velocidad > VELOCIDAD_MAXIMA_KMH) {
            throw new ServicioTrenInvalidoException(String.format(Locale.ROOT,
                    "Con %d minutos de viaje el tren iría a %.0f km/h en un tramo de unos %.0f km de vía "
                            + "(%.0f km en línea recta); ningún servicio de la red supera los %.0f km/h. "
                            + "Revisa la hora de llegada.",
                    transito, velocidad, kmVia, kmRecta.doubleValue(), VELOCIDAD_MAXIMA_KMH));
        }
    }

    /**
     * Elimina el servicio y deja constancia en auditoria. Devuelve false si el
     * identificador ya no existe, para que el controlador no anuncie una baja
     * que no ha ocurrido (por ejemplo al reenviar el formulario desde una
     * pestana con datos caducados).
     */
    @Transactional
    public boolean eliminar(Integer id, String usuario) {
        return servicioTrenRepository.buscarConEstaciones(id).map(servicio -> {
            String valorAnterior = describir(servicio);
            servicioTrenRepository.delete(servicio);
            auditoriaService.registrarAuditoria(usuario, "DELETE", TABLA_AUDITADA, valorAnterior, null);
            return true;
        }).orElse(false);
    }

    private String describir(ServicioTren servicio) {
        return "SerIdEstacionOrigen=" + servicio.getEstacionOrigen().getId()
                + "; SerIdEstacionDestino=" + servicio.getEstacionDestino().getId()
                + "; SerHorarioSalida=" + servicio.getHorarioSalida()
                + "; SerHorarioLlegada=" + servicio.getHorarioLlegada()
                + "; SerTiempoTransitoMin=" + servicio.getTiempoTransitoMin()
                + "; SerTarifa=" + servicio.getTarifa();
    }
}
