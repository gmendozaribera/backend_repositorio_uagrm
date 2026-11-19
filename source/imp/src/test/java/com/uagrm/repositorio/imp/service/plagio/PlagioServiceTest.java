package com.uagrm.repositorio.imp.service.plagio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.FuentePlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.dao.entity.TipoNotificacion;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.AnalisisPlagioRepository;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.mapper.PlagioMapper;
import com.uagrm.repositorio.dto.mapper.PlagioMapperImpl;
import com.uagrm.repositorio.dto.plagio.AnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoAnalisisPlagioResponse;
import com.uagrm.repositorio.dto.plagio.EstadoPlagioCache;
import com.uagrm.repositorio.dto.plagio.FuentePlagioResponse;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.EstadoPlagioCacheService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.notificacion.NotificacionService;
import com.uagrm.repositorio.imp.service.trabajo.AccesoTrabajos;
import com.uagrm.repositorio.mockup.PlagioMock;
import com.uagrm.repositorio.mockup.TrabajoMock;
import com.uagrm.repositorio.mockup.UsuarioMock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reglas de PlagioService con repositorios simulados: permisos (docente del grupo, docente ajeno, admin,
 * estudiante), 409/400/503 al iniciar, consolidacion del resultado (maximo, nivel, fuentes, limite), regla de
 * fuentes no publicadas, idempotencia, ERROR, timeout y la cache de /estado (hit, miss, Redis caido).
 */
class PlagioServiceTest {

    private static final int ID_DOCENTE_AJENO = 6;

    private final AnalisisPlagioRepository analisisRepository = mock(AnalisisPlagioRepository.class);
    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final TrabajoRepository trabajoRepository = mock(TrabajoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final GrupoDocenteRepository grupoDocenteRepository = mock(GrupoDocenteRepository.class);
    private final ConfigFacultadService configFacultad = mock(ConfigFacultadService.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final EstadoPlagioCacheService cache = mock(EstadoPlagioCacheService.class);
    private final MotorPlagio motor = mock(MotorPlagio.class);
    private final DespachadorPlagio despachador = mock(DespachadorPlagio.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MotorPlagio> motores = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DespachadorPlagio> despachadores = mock(ObjectProvider.class);

    private PlagioService service;
    private Trabajo trabajo;

    @BeforeEach
    void setUp() {
        AccesoTrabajos acceso = new AccesoTrabajos(usuarioRepository, trabajoRepository, grupoDocenteRepository);
        service = new PlagioService(analisisRepository, documentoRepository, trabajoRepository, acceso,
                configFacultad, parametros, notificaciones, cache, new PlagioMapperImpl(), motores, despachadores);
        usuario(TrabajoMock.autor());
        usuario(TrabajoMock.docente());
        usuario(TrabajoMock.admin());
        usuario(UsuarioMock.usuario(ID_DOCENTE_AJENO, "doc.ajeno", "DOCENTE"));
        when(grupoDocenteRepository.findEsAdminCurso(40, TrabajoMock.ID_DOCENTE)).thenReturn(Optional.of(false));
        when(grupoDocenteRepository.findEsAdminCurso(40, ID_DOCENTE_AJENO)).thenReturn(Optional.empty());
        when(configFacultad.configDe(1)).thenReturn(config(30, 50, true));
        when(parametros.entero(Parametro.PLAGIO_MAX_FUENTES)).thenReturn(20);
        when(parametros.entero(Parametro.PLAGIO_TIMEOUT_MINUTOS)).thenReturn(30);
        when(motores.getIfAvailable()).thenReturn(motor);
        when(despachadores.getIfAvailable()).thenReturn(despachador);
        when(motor.tipo()).thenReturn(TipoMotorPlagio.SIMULADO);
        when(analisisRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AnalisisPlagio a = inv.getArgument(0);
            a.setIdAnalisis(PlagioMock.ID_ANALISIS);
            return a;
        });
        trabajoEn(EstadoTrabajo.PENDIENTE);
        archivos(pdf());
    }

    // ---------- Iniciar ----------

    @Test
    void docenteDelGrupoIniciaYSeCacheaYDespacha() {
        AnalisisPlagioResponse respuesta = service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);

        assertThat(respuesta.estado()).isEqualTo(EstadoAnalisisPlagio.EN_PROCESO);
        assertThat(respuesta.motor()).isEqualTo(TipoMotorPlagio.SIMULADO);
        assertThat(respuesta.porcentajeSimilitud()).isNull();
        assertThat(respuesta.nivel()).isNull();
        assertThat(respuesta.umbralMedio()).isEqualTo(30);
        assertThat(respuesta.comparacionInternet()).isTrue();
        assertThat(respuesta.documentosAnalizados()).isEqualTo(1);
        assertThat(respuesta.iniciadoPor()).isNotBlank();
        ArgumentCaptor<AnalisisPlagio> guardado = ArgumentCaptor.forClass(AnalisisPlagio.class);
        verify(analisisRepository).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getScanId()).isNull();  // SIMULADO no usa scan externo
        assertThat(guardado.getValue().getDocumentos()).singleElement()
                .satisfies(d -> assertThat(d.getSha256()).isEqualTo("a".repeat(64)));
        verify(cache).guardar(TrabajoMock.ID_TRABAJO,
                new EstadoPlagioCache(PlagioMock.ID_ANALISIS, EstadoAnalisisPlagio.EN_PROCESO, null, null));
        verify(despachador).despachar(PlagioMock.ID_ANALISIS);
    }

    @Test
    void adminIniciaYCopyleaksGeneraScanId() {
        when(motor.tipo()).thenReturn(TipoMotorPlagio.COPYLEAKS);
        service.iniciar(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO);
        ArgumentCaptor<AnalisisPlagio> guardado = ArgumentCaptor.forClass(AnalisisPlagio.class);
        verify(analisisRepository).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getScanId()).matches("uagrm-[0-9a-f]{20}");
        assertThat(guardado.getValue().getMotor()).isEqualTo(TipoMotorPlagio.COPYLEAKS);
    }

    @Test
    void estudianteRecibe403YDocenteAjeno404() {
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.ultimo(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.iniciar(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.estado(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.historial(jwt(ID_DOCENTE_AJENO), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        verify(analisisRepository, never()).saveAndFlush(any());
    }

    @Test
    void estadosDelTrabajoNoAnalizablesDan409() {
        for (EstadoTrabajo estado : List.of(EstadoTrabajo.BORRADOR, EstadoTrabajo.RECHAZADO)) {
            trabajoEn(estado);
            assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO),
                    HttpStatus.CONFLICT);
        }
        for (EstadoTrabajo estado : List.of(EstadoTrabajo.EN_REVISION, EstadoTrabajo.APROBADO,
                EstadoTrabajo.APROBADO_CON_OBSERVACIONES, EstadoTrabajo.EVALUADO)) {
            trabajoEn(estado);
            service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);
        }
    }

    @Test
    void sinArchivosAnalizablesDa400() {
        archivos();
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO), HttpStatus.BAD_REQUEST);
        verify(documentoRepository).findByTrabajoIdTrabajoAndTipoAndExtensionInOrderByFechaSubidaAscIdDocumentoAsc(
                TrabajoMock.ID_TRABAJO, TipoDocumento.ARCHIVO, Set.of("pdf", "docx", "odt"));
    }

    @Test
    void yaEnCursoDa409PorChequeoOPorIndiceUnico() {
        when(analisisRepository.existsByTrabajoIdTrabajoAndEstado(TrabajoMock.ID_TRABAJO,
                EstadoAnalisisPlagio.EN_PROCESO)).thenReturn(true);
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO), HttpStatus.CONFLICT);

        when(analisisRepository.existsByTrabajoIdTrabajoAndEstado(anyInt(), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uq_en_proceso"))
                .when(analisisRepository).saveAndFlush(any());
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO), HttpStatus.CONFLICT);
        verifyNoInteractions(despachador);
    }

    @Test
    void motorSinFuentesDeComparacionDa400SinCrearElAnalisis() {
        when(configFacultad.configDe(1)).thenReturn(config(30, 50, false));
        org.mockito.Mockito.doThrow(NegocioException.datosInvalidos("No hay contra que comparar."))
                .when(motor).verificarFuentes(false);
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO), HttpStatus.BAD_REQUEST);
        verify(motor).verificarFuentes(false);
        verify(analisisRepository, never()).saveAndFlush(any());
        verifyNoInteractions(despachador, cache);
    }

    @Test
    void sinMotorDa503() {
        when(motores.getIfAvailable()).thenReturn(null);
        assertStatus(() -> service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void fallaAlDespacharNoRompeLaRespuesta() {
        org.mockito.Mockito.doThrow(new IllegalStateException("quartz")).when(despachador).despachar(anyInt());
        assertThat(service.iniciar(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO).estado())
                .isEqualTo(EstadoAnalisisPlagio.EN_PROCESO);
    }

    // ---------- Resultado ----------

    @Test
    void completaConElMaximoDeLosDocumentosNivelFuentesYNotificacion() {
        AnalisisPlagio analisis = enProceso();
        PlagioMock.agregarDocumento(analisis, 2, "b".repeat(64));
        // los umbrales cambiaron desde que se inicio: se usan los vigentes al terminar
        when(configFacultad.configDe(1)).thenReturn(config(25, 40, true));

        service.registrarDocumento(PlagioMock.ID_ANALISIS, 1, new BigDecimal("12.345"), List.of(
                interna(12, "Sistema de ventas", "10"),
                web("https://example.org/a", "8"),
                interna(TrabajoMock.ID_TRABAJO, "El propio trabajo", "99")));  // se ignora
        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisisPlagio.EN_PROCESO);
        verifyNoInteractions(notificaciones);

        service.registrarDocumento(PlagioMock.ID_ANALISIS, 2, new BigDecimal("41.5"), List.of(
                interna(12, "Sistema de ventas", "30"),   // misma fuente: cuenta una vez con el mayor
                web("https://example.org/b", "5")));

        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisisPlagio.COMPLETADO);
        assertThat(analisis.getPorcentaje()).isEqualByComparingTo("41.50");
        assertThat(analisis.getNivel()).isEqualTo(NivelPlagio.ALTO);
        assertThat(analisis.getUmbralMedio()).isEqualTo(25);
        assertThat(analisis.getUmbralAlto()).isEqualTo(40);
        assertThat(analisis.getFechaFin()).isNotNull();
        assertThat(analisis.getDocumentos()).extracting(d -> d.getPorcentaje().toPlainString())
                .containsExactly("12.35", "41.50");
        assertThat(PlagioService.consolidar(analisis.getFuentes(), 20))
                .extracting(FuentePlagio::getPorcentaje).extracting(BigDecimal::toPlainString)
                .containsExactly("30.00", "8.00", "5.00");
        assertThat(analisis.getFuentes()).hasSize(3);

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).notificar(eq(TrabajoMock.ID_DOCENTE), eq(TipoNotificacion.PLAGIO_COMPLETADO),
                anyString(), mensaje.capture(), eq("/trabajos/7"));
        assertThat(mensaje.getValue()).contains("ALTO").doesNotContain("41");  // el nivel, nunca el porcentaje
        verify(cache).guardar(TrabajoMock.ID_TRABAJO,
                new EstadoPlagioCache(PlagioMock.ID_ANALISIS, EstadoAnalisisPlagio.COMPLETADO, 41.5, NivelPlagio.ALTO));
    }

    @Test
    void nivelSegunUmbrales() {
        assertThat(NivelPlagio.de(new BigDecimal("29.99"), 30, 50)).isEqualTo(NivelPlagio.OK);
        assertThat(NivelPlagio.de(new BigDecimal("30"), 30, 50)).isEqualTo(NivelPlagio.MEDIO);
        assertThat(NivelPlagio.de(new BigDecimal("49.99"), 30, 50)).isEqualTo(NivelPlagio.MEDIO);
        assertThat(NivelPlagio.de(new BigDecimal("50"), 30, 50)).isEqualTo(NivelPlagio.ALTO);
        assertThat(PlagioService.acotar(new BigDecimal("120"))).isEqualByComparingTo("100");
        assertThat(PlagioService.acotar(new BigDecimal("-3"))).isEqualByComparingTo("0");
        assertThat(PlagioService.acotar(null)).isEqualByComparingTo("0");
    }

    @Test
    void fuentesLimitadasAMaxFuentes() {
        when(parametros.entero(Parametro.PLAGIO_MAX_FUENTES)).thenReturn(2);
        AnalisisPlagio analisis = enProceso();
        service.registrarDocumento(PlagioMock.ID_ANALISIS, 1, new BigDecimal("60"), List.of(
                web("https://example.org/1", "5"), web("https://example.org/2", "50"), web("https://example.org/3", "20")));
        assertThat(analisis.getFuentes()).extracting(FuentePlagio::getUrl)
                .containsExactlyInAnyOrder("https://example.org/2", "https://example.org/3");
    }

    @Test
    void resultadoRepetidoOTardioNoCambiaNada() {
        AnalisisPlagio analisis = enProceso();
        PlagioMock.agregarDocumento(analisis, 2, "b".repeat(64));
        service.registrarDocumento(PlagioMock.ID_ANALISIS, 1, new BigDecimal("10"), List.of());
        service.registrarDocumento(PlagioMock.ID_ANALISIS, 1, new BigDecimal("90"), List.of());  // repetido
        assertThat(analisis.getDocumentos().getFirst().getPorcentaje()).isEqualByComparingTo("10");

        analisis.setEstado(EstadoAnalisisPlagio.ERROR);  // p. ej. ya vencio por timeout
        service.registrarDocumento(PlagioMock.ID_ANALISIS, 2, new BigDecimal("90"), List.of());
        assertThat(analisis.getDocumentos().get(1).getPorcentaje()).isNull();
        verifyNoInteractions(notificaciones);

        assertStatus(() -> service.registrarDocumento(99, 1, BigDecimal.ONE, List.of()), HttpStatus.NOT_FOUND);
    }

    @Test
    void documentoInexistenteDa404() {
        enProceso();
        assertStatus(() -> service.registrarDocumento(PlagioMock.ID_ANALISIS, 5, BigDecimal.ONE, List.of()),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void fallarEsIdempotenteYNotifica() {
        AnalisisPlagio analisis = enProceso();
        assertThat(service.fallar(PlagioMock.ID_ANALISIS, PlagioService.MENSAJE_PROVEEDOR)).isTrue();
        assertThat(service.fallar(PlagioMock.ID_ANALISIS, "otro")).isFalse();
        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisisPlagio.ERROR);
        assertThat(analisis.getMensajeError()).isEqualTo(PlagioService.MENSAJE_PROVEEDOR);
        assertThat(analisis.getPorcentaje()).isNull();
        verify(notificaciones, times(1)).notificar(eq(TrabajoMock.ID_DOCENTE), eq(TipoNotificacion.PLAGIO_COMPLETADO),
                anyString(), anyString(), eq("/trabajos/7"));
        verify(cache).guardar(TrabajoMock.ID_TRABAJO,
                new EstadoPlagioCache(PlagioMock.ID_ANALISIS, EstadoAnalisisPlagio.ERROR, null, null));
    }

    @Test
    void timeoutPasaAErrorLosVencidos() {
        AnalisisPlagio analisis = enProceso();
        when(analisisRepository.findIdsPorEstadoIniciadosAntesDe(eq(EstadoAnalisisPlagio.EN_PROCESO), any()))
                .thenReturn(List.of(PlagioMock.ID_ANALISIS));
        Instant antes = Instant.now();

        assertThat(service.marcarExcedidos()).isEqualTo(1);

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(analisisRepository).findIdsPorEstadoIniciadosAntesDe(eq(EstadoAnalisisPlagio.EN_PROCESO),
                limite.capture());
        assertThat(limite.getValue()).isBefore(antes.minusSeconds(29 * 60));
        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisisPlagio.ERROR);
        assertThat(analisis.getMensajeError()).isEqualTo("El analisis excedio el tiempo maximo.");
    }

    // ---------- Lectura ----------

    @Test
    void fuentesInternasNoPublicadasSinTituloNiId() {
        AnalisisPlagio analisis = completado();
        analisis.getFuentes().add(PlagioMock.fuente(analisis, TipoFuentePlagio.INTERNA, "Sistema de ventas", null, 12, "30"));
        analisis.getFuentes().add(PlagioMock.fuente(analisis, TipoFuentePlagio.INTERNA, "Tesis secreta", null, 13, "20"));
        analisis.getFuentes().add(PlagioMock.fuente(analisis, TipoFuentePlagio.INTERNA, "Sin identificar", null, null, "15"));
        analisis.getFuentes().add(PlagioMock.fuente(analisis, TipoFuentePlagio.INTERNET, "Web", "https://example.org/x", null, "10"));
        when(analisisRepository.findFirstByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(TrabajoMock.ID_TRABAJO))
                .thenReturn(Optional.of(analisis));
        when(trabajoRepository.findIdsEnEstado(List.of(12, 13), EstadoTrabajo.EVALUADO)).thenReturn(Set.of(12));

        List<FuentePlagioResponse> fuentes = service.ultimo(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO).fuentes();

        assertThat(fuentes).containsExactly(
                new FuentePlagioResponse(TipoFuentePlagio.INTERNA, "Sistema de ventas", null, 12, 30.0),
                new FuentePlagioResponse(TipoFuentePlagio.INTERNA, PlagioMapper.TITULO_NO_PUBLICADO, null, null, 20.0),
                new FuentePlagioResponse(TipoFuentePlagio.INTERNA, PlagioMapper.TITULO_NO_PUBLICADO, null, null, 15.0),
                new FuentePlagioResponse(TipoFuentePlagio.INTERNET, "Web", "https://example.org/x", null, 10.0));
    }

    @Test
    void ultimoEHistorial() {
        assertStatus(() -> service.ultimo(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);
        when(analisisRepository.findByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(TrabajoMock.ID_TRABAJO))
                .thenReturn(List.of(completado()));
        assertThat(service.historial(jwt(TrabajoMock.ID_ADMIN), TrabajoMock.ID_TRABAJO)).singleElement()
                .satisfies(a -> {
                    assertThat(a.porcentajeSimilitud()).isEqualTo(34.2);
                    assertThat(a.nivel()).isEqualTo(NivelPlagio.MEDIO);
                });
        verify(trabajoRepository, never()).findIdsEnEstado(any(), any());  // sin fuentes internas no hay consulta
    }

    @Test
    void estadoDesdeRedisSinTocarLasTablasDePlagio() {
        when(cache.leer(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(
                new EstadoPlagioCache(PlagioMock.ID_ANALISIS, EstadoAnalisisPlagio.EN_PROCESO, null, null)));
        EstadoAnalisisPlagioResponse estado = service.estado(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);
        assertThat(estado).isEqualTo(new EstadoAnalisisPlagioResponse(PlagioMock.ID_ANALISIS,
                EstadoAnalisisPlagio.EN_PROCESO, null, null));
        verifyNoInteractions(analisisRepository);
    }

    @Test
    void estadoSinCacheORedisCaidoLeeLaBdYRecachea() {
        // EstadoPlagioCacheService devuelve vacio tanto si falta la key como si Redis no responde
        when(cache.leer(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.empty());
        assertStatus(() -> service.estado(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO), HttpStatus.NOT_FOUND);

        when(analisisRepository.findFirstByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(TrabajoMock.ID_TRABAJO))
                .thenReturn(Optional.of(completado()));
        EstadoAnalisisPlagioResponse estado = service.estado(jwt(TrabajoMock.ID_DOCENTE), TrabajoMock.ID_TRABAJO);
        assertThat(estado.estado()).isEqualTo(EstadoAnalisisPlagio.COMPLETADO);
        assertThat(estado.porcentajeSimilitud()).isEqualTo(34.2);
        verify(cache).guardar(TrabajoMock.ID_TRABAJO,
                new EstadoPlagioCache(PlagioMock.ID_ANALISIS, EstadoAnalisisPlagio.COMPLETADO, 34.2, NivelPlagio.MEDIO));
    }

    @Test
    void solicitudSoloConDocumentosPendientes() {
        AnalisisPlagio analisis = enProceso();
        PlagioMock.agregarDocumento(analisis, 2, "b".repeat(64));
        analisis.getDocumentos().getFirst().setPorcentaje(BigDecimal.TEN);
        when(analisisRepository.findById(PlagioMock.ID_ANALISIS)).thenReturn(Optional.of(analisis));
        SolicitudAnalisis solicitud = service.solicitud(PlagioMock.ID_ANALISIS).orElseThrow();
        assertThat(solicitud.idFacultad()).isEqualTo(1);
        assertThat(solicitud.documentos()).extracting(SolicitudAnalisis.DocumentoAnalisis::indice).containsExactly(2);

        analisis.setEstado(EstadoAnalisisPlagio.COMPLETADO);
        assertThat(service.solicitud(PlagioMock.ID_ANALISIS)).isEmpty();
    }

    // ---------- helpers ----------

    private AnalisisPlagio enProceso() {
        AnalisisPlagio analisis = PlagioMock.enProceso(trabajo, TipoMotorPlagio.SIMULADO);
        when(analisisRepository.findParaActualizar(PlagioMock.ID_ANALISIS)).thenReturn(Optional.of(analisis));
        return analisis;
    }

    private AnalisisPlagio completado() {
        AnalisisPlagio analisis = PlagioMock.enProceso(trabajo, TipoMotorPlagio.SIMULADO);
        analisis.setEstado(EstadoAnalisisPlagio.COMPLETADO);
        analisis.setPorcentaje(new BigDecimal("34.20"));
        analisis.setNivel(NivelPlagio.MEDIO);
        analisis.setFechaFin(Instant.now());
        return analisis;
    }

    private static FuenteDetectada interna(Integer idTrabajo, String titulo, String porcentaje) {
        return new FuenteDetectada(TipoFuentePlagio.INTERNA, titulo, null, idTrabajo, new BigDecimal(porcentaje));
    }

    private static FuenteDetectada web(String url, String porcentaje) {
        return new FuenteDetectada(TipoFuentePlagio.INTERNET, "Pagina", url, null, new BigDecimal(porcentaje));
    }

    private Documento pdf() {
        return TrabajoMock.pdf(trabajo);
    }

    private void archivos(Documento... documentos) {
        when(documentoRepository.findByTrabajoIdTrabajoAndTipoAndExtensionInOrderByFechaSubidaAscIdDocumentoAsc(
                eq(TrabajoMock.ID_TRABAJO), eq(TipoDocumento.ARCHIVO), any())).thenReturn(List.of(documentos));
    }

    private void usuario(Usuario usuario) {
        when(usuarioRepository.findConRolByIdUsuario(usuario.getIdUsuario())).thenReturn(Optional.of(usuario));
    }

    private void trabajoEn(EstadoTrabajo estado) {
        trabajo = TrabajoMock.trabajo(estado);
        when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(trabajoRepository.findParaActualizar(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
    }

    private static ConfigFacultadResponse config(int medio, int alto, boolean internet) {
        return new ConfigFacultadResponse(1, true, 10, medio, alto, internet, false, 100, 1);
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }

    private static void assertStatus(Runnable accion, HttpStatus status) {
        assertThatThrownBy(accion::run).isInstanceOf(NegocioException.class).extracting("status").isEqualTo(status);
    }
}
