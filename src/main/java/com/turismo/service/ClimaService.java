package com.turismo.service;

import com.turismo.model.Estacion;
import com.turismo.model.PrevisionClima;
import com.turismo.repository.PrevisionClimaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * RF-06/RF-15: capa de negocio sobre PrevisionClima. La validacion del
 * feed (rango 0-100 de probabilidad de lluvia) vive en
 * SenamhiClient.procesarFeedSenamhi(); este servicio persiste el
 * insert/update ya validado.
 */
@Service
public class ClimaService {

    private final PrevisionClimaRepository previsionClimaRepository;

    public ClimaService(PrevisionClimaRepository previsionClimaRepository) {
        this.previsionClimaRepository = previsionClimaRepository;
    }

    public Optional<PrevisionClima> buscarPorEstacionYFecha(Integer idEstacion, LocalDate fecha) {
        return previsionClimaRepository.findByEstacion_IdAndFecha(idEstacion, fecha);
    }

    @Transactional
    public PrevisionClima guardarOActualizar(Estacion estacion, LocalDate fecha, PrevisionClima datos) {
        PrevisionClima existente = previsionClimaRepository
                .findByEstacion_IdAndFecha(estacion.getId(), fecha)
                .orElseGet(PrevisionClima::new);

        existente.setEstacion(estacion);
        existente.setFecha(fecha);
        existente.setTemperaturaMinC(datos.getTemperaturaMinC());
        existente.setTemperaturaMaxC(datos.getTemperaturaMaxC());
        existente.setProbabilidadLluvia(datos.getProbabilidadLluvia());
        existente.setEstadoClima(datos.getEstadoClima());

        return previsionClimaRepository.save(existente);
    }
}
