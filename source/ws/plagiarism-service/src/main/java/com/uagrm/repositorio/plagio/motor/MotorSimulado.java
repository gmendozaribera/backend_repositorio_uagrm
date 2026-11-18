package com.uagrm.repositorio.plagio.motor;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.service.plagio.MotorPlagio;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import com.uagrm.repositorio.plagio.job.QuartzDespachador;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Motor SIMULADO (PLAGIO_MOTOR=SIMULADO; por defecto solo en el perfil dev). No usa la red: tras
 * PLAGIO_GROUP.SIMULADO_SEGUNDOS (job de Quartz) completa con el resultado deterministico de {@link SimuladorPlagio}.
 * El resultado queda con motor = SIMULADO, asi nadie lo confunde con un analisis real.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.plagio.motor", havingValue = "SIMULADO")
@RequiredArgsConstructor
public class MotorSimulado implements MotorPlagio {

    /** Candidatos a fuente interna que se consideran (los de menor id de la facultad). */
    static final int MAXIMO_CANDIDATOS = 50;

    private final PlagioService plagioService;
    private final TrabajoRepository trabajoRepository;
    private final ParametrosService parametros;
    private final QuartzDespachador despachador;

    @Override
    public TipoMotorPlagio tipo() {
        return TipoMotorPlagio.SIMULADO;
    }

    /** Solo agenda la finalizacion: el "analisis" termina a los SIMULADO_SEGUNDOS. */
    @Override
    public void enviar(SolicitudAnalisis solicitud) {
        int segundos = parametros.entero(Parametro.PLAGIO_SIMULADO_SEGUNDOS);
        despachador.agendar(CompletarSimuladoJob.class, "simulado", solicitud.idAnalisis(),
                Instant.now().plus(Duration.ofSeconds(segundos)));
    }

    /** Lo llama {@link CompletarSimuladoJob}: registra el resultado de cada documento aun pendiente. */
    public void completar(Integer idAnalisis) {
        Optional<SolicitudAnalisis> pendiente = plagioService.solicitud(idAnalisis);
        if (pendiente.isEmpty()) {
            return;  // ya termino (p. ej. timeout) o no existe
        }
        SolicitudAnalisis solicitud = pendiente.get();
        List<SimuladorPlagio.Candidato> candidatos = trabajoRepository.findOtrosDeFacultad(solicitud.idFacultad(),
                        solicitud.idTrabajo(), EstadoTrabajo.BORRADOR, PageRequest.of(0, MAXIMO_CANDIDATOS))
                .stream()
                .map(t -> new SimuladorPlagio.Candidato(t.getIdTrabajo(), t.getTitulo()))
                .toList();
        for (SolicitudAnalisis.DocumentoAnalisis documento : solicitud.documentos()) {
            SimuladorPlagio.Resultado resultado = SimuladorPlagio.analizar(documento.sha256(), candidatos,
                    solicitud.comparacionInternet());
            plagioService.registrarDocumento(idAnalisis, documento.indice(), resultado.porcentaje(),
                    resultado.fuentes());
        }
        log.info("Analisis SIMULADO {} completado ({} documentos)", idAnalisis, solicitud.documentos().size());
    }
}
