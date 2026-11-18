package com.uagrm.repositorio.plagio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uagrm.repositorio.dao.entity.AnalisisPlagio;
import com.uagrm.repositorio.dao.entity.AnalisisPlagioDocumento;
import com.uagrm.repositorio.dao.entity.EstadoAnalisisPlagio;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.FuentePlagio;
import com.uagrm.repositorio.dao.entity.NivelPlagio;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoFuentePlagio;
import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.AnalisisPlagioRepository;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Esquema real V1..V11 contra PostgreSQL 15 (Testcontainers, regla 9; se salta sin Docker): las entidades de plagio
 * validan (ddl-auto=validate), el indice unico parcial garantiza UN solo EN_PROCESO por trabajo, el CHECK de
 * coherencia por estado, el seed de PLAGIO_GROUP y las consultas que usan el service y los motores.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlagioRepositorioTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;
    @Autowired
    private AnalisisPlagioRepository analisisRepository;
    @Autowired
    private TrabajoRepository trabajoRepository;
    @Autowired
    private DocumentoRepository documentoRepository;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                INSERT INTO facultad (id_facultad, nombre, sigla) VALUES (1, 'FICCT', 'FICCT'), (2, 'FCET', 'FCET');
                INSERT INTO carrera (id_carrera, nombre, sigla, id_facultad) VALUES (10, 'Informatica', 'INF', 1);
                INSERT INTO persona (id_persona, nombre, apellidos, ci, correo, registro, id_carrera) VALUES
                    (100, 'Juan', 'Perez', 1000, 'juan@uagrm.edu.bo', 219000100, 10);
                INSERT INTO usuario (id_usuario, usuario, password, requiere_cambio_password, id_tipo_usuario, id_persona)
                    SELECT 500, 'juan', 'x', false, t.id_tipo_usuario, 100 FROM tipo_usuario t WHERE t.nombre = 'DOCENTE';
                INSERT INTO semestre (id_semestre, semestre, anio, id_facultad) VALUES (30, 2, 2026, 1), (32, 2, 2026, 2);
                INSERT INTO materia (id_materia, nombre, sigla, id_facultad) VALUES
                    (20, 'Ingenieria de Software I', 'INF412', 1), (22, 'Robotica', 'ECO100', 2);
                INSERT INTO grupo (id_grupo, nombre, id_materia, id_semestre) VALUES (40, 'SA', 20, 30), (42, 'SC', 22, 32);
                INSERT INTO tipo_trabajo (id_tipo_trabajo, nombre, id_facultad) VALUES (60, 'Proyecto', 1), (62, 'Proyecto', 2);
                INSERT INTO fase (id_fase, nombre, id_facultad) VALUES (50, 'Perfil', 1);
                INSERT INTO trabajo (id_trabajo, titulo, resumen, estado, id_persona_creador, id_grupo, id_materia,
                                     id_semestre, id_tipo_trabajo) VALUES
                    (1, 'Analizado', 'r', 'EN_REVISION', 100, 40, 20, 30, 60),
                    (2, 'Publicado', 'r', 'EVALUADO', 100, 40, 20, 30, 60),
                    (3, 'Borrador', 'r', 'BORRADOR', 100, 40, 20, 30, 60),
                    (4, 'Pendiente', 'r', 'PENDIENTE', 100, 40, 20, 30, 60),
                    (5, 'Otra facultad', 'r', 'EVALUADO', 100, 42, 22, 32, 62);
                INSERT INTO documento (id_documento, id_trabajo, id_fase, titulo, tipo, categoria, nombre_archivo, extension,
                                       content_type, ruta_archivo, tamano_bytes, sha256, fecha_subida) VALUES
                    (70, 1, 50, 'Informe', 'ARCHIVO', 'DOCUMENTO', 'informe.pdf', 'pdf', 'application/pdf',
                        'trabajos/1/a.pdf', 10, repeat('a', 64), '2026-11-01T10:00:00Z'),
                    (71, 1, 50, 'Anexo', 'ARCHIVO', 'DOCUMENTO', 'anexo.odt', 'odt',
                        'application/vnd.oasis.opendocument.text', 'trabajos/1/b.odt', 10, repeat('b', 64),
                        '2026-11-02T10:00:00Z'),
                    (72, 1, 50, 'Foto', 'ARCHIVO', 'IMAGEN', 'foto.png', 'png', 'image/png', 'trabajos/1/c.png', 10,
                        repeat('c', 64), '2026-11-03T10:00:00Z');
                """);
    }

    @Test
    void seedDePlagioGroup() {
        assertThat(jdbc.queryForList("SELECT domain_name || '=' || domain_value FROM dominio "
                + "WHERE domain_group = 'PLAGIO_GROUP' ORDER BY domain_name", String.class))
                .containsExactly("MAX_FUENTES=20", "SIMULADO_SEGUNDOS=8", "TIMEOUT_MINUTOS=30");
    }

    @Test
    void unSoloEnProcesoPorTrabajo() {
        AnalisisPlagio primero = analisisRepository.saveAndFlush(nuevo(1));
        assertThat(analisisRepository.existsByTrabajoIdTrabajoAndEstado(1, EstadoAnalisisPlagio.EN_PROCESO)).isTrue();
        assertThatThrownBy(() -> analisisRepository.saveAndFlush(nuevo(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void terminadoPermiteOtroEnCursoYGuardaDocumentosYFuentes() {
        AnalisisPlagio primero = nuevo(1);
        AnalisisPlagioDocumento doc = primero.getDocumentos().getFirst();
        doc.setPorcentaje(new BigDecimal("41.50"));
        primero.setEstado(EstadoAnalisisPlagio.COMPLETADO);
        primero.setPorcentaje(new BigDecimal("41.50"));
        primero.setNivel(NivelPlagio.MEDIO);
        primero.setFechaFin(Instant.now());
        FuentePlagio fuente = new FuentePlagio();
        fuente.setAnalisis(primero);
        fuente.setTipo(TipoFuentePlagio.INTERNA);
        fuente.setTitulo("Publicado");
        fuente.setIdTrabajoFuente(2);
        fuente.setPorcentaje(new BigDecimal("30.00"));
        primero.getFuentes().add(fuente);
        analisisRepository.saveAndFlush(primero);
        analisisRepository.saveAndFlush(nuevo(1));
        em.clear();

        List<AnalisisPlagio> historial = analisisRepository.findByTrabajoIdTrabajoOrderByFechaInicioDescIdAnalisisDesc(1);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(1).getFuentes()).singleElement()
                .satisfies(f -> assertThat(f.getPorcentaje()).isEqualByComparingTo("30"));
        assertThat(historial.get(1).getDocumentos().getFirst().getSha256()).isEqualTo("a".repeat(64));
        assertThat(historial.get(0).getUsuarioInicio().getPersona().getNombre()).isEqualTo("Juan");
    }

    @Test
    void checkDeCoherencia() {
        AnalisisPlagio incoherente = nuevo(1);
        incoherente.setEstado(EstadoAnalisisPlagio.COMPLETADO);  // sin porcentaje, nivel ni fecha_fin
        assertThatThrownBy(() -> analisisRepository.saveAndFlush(incoherente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consultasDelServiceYDeLosMotores() {
        AnalisisPlagio analisis = nuevo(1);
        analisis.setScanId("uagrm-0123456789abcdef0123");
        analisis.setFechaInicio(Instant.now().minusSeconds(3600));
        analisisRepository.saveAndFlush(analisis);
        Integer id = analisis.getIdAnalisis();

        assertThat(analisisRepository.findIdsPorEstadoIniciadosAntesDe(EstadoAnalisisPlagio.EN_PROCESO,
                Instant.now().minusSeconds(30 * 60))).containsExactly(id);
        assertThat(analisisRepository.findIdsPorEstadoIniciadosAntesDe(EstadoAnalisisPlagio.EN_PROCESO,
                Instant.now().minusSeconds(2 * 3600))).isEmpty();
        assertThat(analisisRepository.findIdsPorEstadoYMotor(EstadoAnalisisPlagio.EN_PROCESO, TipoMotorPlagio.SIMULADO))
                .containsExactly(id);
        assertThat(analisisRepository.findIdByScanId("uagrm-0123456789abcdef0123")).contains(id);
        assertThat(analisisRepository.findIdTrabajoByScanId("uagrm-0123456789abcdef0123")).contains(1);
        assertThat(analisisRepository.findParaActualizar(id)).isPresent();

        assertThat(trabajoRepository.findIdsEnEstado(List.of(1, 2, 5), EstadoTrabajo.EVALUADO))
                .isEqualTo(Set.of(2, 5));
        // misma facultad, sin borradores ni el propio
        assertThat(trabajoRepository.findOtrosDeFacultad(1, 1, EstadoTrabajo.BORRADOR, PageRequest.of(0, 50)))
                .extracting(Trabajo::getIdTrabajo).containsExactly(2, 4);
        // solo pdf/docx/odt, en orden de subida
        assertThat(documentoRepository.findByTrabajoIdTrabajoAndTipoAndExtensionInOrderByFechaSubidaAscIdDocumentoAsc(
                1, TipoDocumento.ARCHIVO, Set.of("pdf", "docx", "odt")))
                .extracting(d -> d.getIdDocumento()).containsExactly(70, 71);
    }

    private AnalisisPlagio nuevo(int idTrabajo) {
        AnalisisPlagio analisis = new AnalisisPlagio();
        analisis.setTrabajo(em.getReference(Trabajo.class, idTrabajo));
        analisis.setEstado(EstadoAnalisisPlagio.EN_PROCESO);
        analisis.setMotor(TipoMotorPlagio.SIMULADO);
        analisis.setUmbralMedio(30);
        analisis.setUmbralAlto(50);
        analisis.setComparacionInternet(false);
        analisis.setDocumentosAnalizados(1);
        analisis.setUsuarioInicio(em.getReference(Usuario.class, 500));
        analisis.setFechaInicio(Instant.now());
        AnalisisPlagioDocumento doc = new AnalisisPlagioDocumento();
        doc.setAnalisis(analisis);
        doc.setIndice(1);
        doc.setIdDocumento(70);
        doc.setNombreArchivo("informe.pdf");
        doc.setRutaArchivo("trabajos/1/a.pdf");
        doc.setSha256("a".repeat(64));
        analisis.getDocumentos().add(doc);
        return analisis;
    }
}
