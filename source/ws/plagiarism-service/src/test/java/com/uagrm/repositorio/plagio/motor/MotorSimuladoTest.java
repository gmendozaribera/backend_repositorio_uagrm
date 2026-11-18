package com.uagrm.repositorio.plagio.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.service.plagio.FuenteDetectada;
import com.uagrm.repositorio.imp.service.plagio.PlagioService;
import com.uagrm.repositorio.imp.service.plagio.SolicitudAnalisis;
import com.uagrm.repositorio.plagio.job.QuartzDespachador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/** Motor SIMULADO: resultado deterministico por hash, fuentes reales de la facultad, internet solo si corresponde. */
class MotorSimuladoTest {

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final List<SimuladorPlagio.Candidato> CANDIDATOS = List.of(
            new SimuladorPlagio.Candidato(12, "Sistema de ventas"),
            new SimuladorPlagio.Candidato(13, "Control de inventarios"),
            new SimuladorPlagio.Candidato(14, "App de biblioteca"));

    private final PlagioService plagioService = mock(PlagioService.class);
    private final TrabajoRepository trabajoRepository = mock(TrabajoRepository.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final QuartzDespachador despachador = mock(QuartzDespachador.class);
    private final MotorSimulado motor = new MotorSimulado(plagioService, trabajoRepository, parametros, despachador);

    @Test
    void mismoDocumentoMismoResultado() {
        SimuladorPlagio.Resultado uno = SimuladorPlagio.analizar(SHA_A, CANDIDATOS, true);
        SimuladorPlagio.Resultado dos = SimuladorPlagio.analizar(SHA_A, CANDIDATOS, true);
        assertThat(uno).isEqualTo(dos);
        assertThat(uno.porcentaje()).isBetween(BigDecimal.ZERO, new BigDecimal("85.00"));
        assertThat(uno.porcentaje().scale()).isEqualTo(2);
        // otro documento, otro resultado
        assertThat(SimuladorPlagio.analizar(SHA_B, CANDIDATOS, true).porcentaje()).isNotEqualTo(uno.porcentaje());
    }

    @Test
    void fuentesInternasRealesYDeInternetSoloSiCorresponde() {
        SimuladorPlagio.Resultado conInternet = SimuladorPlagio.analizar(SHA_A, CANDIDATOS, true);
        assertThat(conInternet.fuentes()).filteredOn(f -> f.tipo() == TipoFuentePlagio.INTERNA)
                .isNotEmpty()
                .allSatisfy(f -> {
                    assertThat(CANDIDATOS).extracting(SimuladorPlagio.Candidato::idTrabajo).contains(f.idTrabajoFuente());
                    assertThat(f.url()).isNull();
                });
        assertThat(conInternet.fuentes()).filteredOn(f -> f.tipo() == TipoFuentePlagio.INTERNET).hasSize(2)
                .allSatisfy(f -> assertThat(f.url()).startsWith("https://example.org/"));
        assertThat(conInternet.fuentes()).allSatisfy(
                f -> assertThat(f.porcentaje()).isLessThanOrEqualTo(conInternet.porcentaje()));

        assertThat(SimuladorPlagio.analizar(SHA_A, CANDIDATOS, false).fuentes())
                .noneMatch(f -> f.tipo() == TipoFuentePlagio.INTERNET);
        assertThat(SimuladorPlagio.analizar(SHA_A, List.of(), false).fuentes()).isEmpty();
    }

    @Test
    void enviarAgendaLaFinalizacionTrasSimuladoSegundos() {
        when(parametros.entero(Parametro.PLAGIO_SIMULADO_SEGUNDOS)).thenReturn(8);
        Instant antes = Instant.now();
        motor.enviar(solicitud(true));
        ArgumentCaptor<Instant> cuando = ArgumentCaptor.forClass(Instant.class);
        verify(despachador).agendar(eq(CompletarSimuladoJob.class), eq("simulado"), eq(90), cuando.capture());
        assertThat(cuando.getValue()).isAfterOrEqualTo(antes.plusSeconds(8));
    }

    @Test
    void completarRegistraCadaDocumentoConTrabajosDeLaFacultad() {
        when(plagioService.solicitud(90)).thenReturn(Optional.of(solicitud(false)));
        Trabajo otro = new Trabajo();
        otro.setIdTrabajo(12);
        otro.setTitulo("Sistema de ventas");
        when(trabajoRepository.findOtrosDeFacultad(eq(1), eq(7), eq(EstadoTrabajo.BORRADOR), any(Pageable.class)))
                .thenReturn(List.of(otro));

        motor.completar(90);

        SimuladorPlagio.Resultado esperadoA = SimuladorPlagio.analizar(SHA_A,
                List.of(new SimuladorPlagio.Candidato(12, "Sistema de ventas")), false);
        verify(plagioService).registrarDocumento(90, 1, esperadoA.porcentaje(), esperadoA.fuentes());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FuenteDetectada>> fuentes = ArgumentCaptor.forClass(List.class);
        verify(plagioService).registrarDocumento(eq(90), eq(2), any(), fuentes.capture());
        assertThat(fuentes.getValue()).noneMatch(f -> f.tipo() == TipoFuentePlagio.INTERNET);
    }

    @Test
    void completarNoHaceNadaSiYaTermino() {
        when(plagioService.solicitud(90)).thenReturn(Optional.empty());
        motor.completar(90);
        verify(plagioService, never()).registrarDocumento(anyInt(), anyInt(), any(), any());
    }

    private static SolicitudAnalisis solicitud(boolean internet) {
        return new SolicitudAnalisis(90, 7, 1, null, internet, List.of(
                new SolicitudAnalisis.DocumentoAnalisis(1, "a.pdf", "trabajos/7/a.pdf", SHA_A),
                new SolicitudAnalisis.DocumentoAnalisis(2, "b.docx", "trabajos/7/b.docx", SHA_B)));
    }
}
