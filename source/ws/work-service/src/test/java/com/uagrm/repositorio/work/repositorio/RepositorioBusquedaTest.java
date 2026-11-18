package com.uagrm.repositorio.work.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import com.uagrm.repositorio.dao.entity.Hashtag;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.TrabajoHistorialRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.TrabajoMapperImpl;
import com.uagrm.repositorio.dto.trabajo.AutorResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.trabajo.AccesoExternoService;
import com.uagrm.repositorio.imp.service.trabajo.AccesoTrabajos;
import com.uagrm.repositorio.imp.service.trabajo.RepositorioService;
import com.uagrm.repositorio.mockup.ConfigFacultadMock;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Busqueda del repositorio contra un PostgreSQL 15 real (Testcontainers, regla 9) con el esquema de las
 * migraciones reales V1..V10: configuracion es_unaccent, vector tsvector mantenido por triggers, busqueda
 * full-text (raices, tildes, frases, exclusion, ranking), cada filtro y su combinacion, la exclusion de lo que no
 * esta EVALUADO, el orden, la paginacion, la carga en lote, la ficha y el acceso anonimo por facultad.
 * Se salta si no hay Docker. Cada test corre en una transaccion que se revierte al final.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositorioBusquedaTest {

    /** Misma imagen que docker-compose dev. */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // Trabajos de prueba (ver datos())
    private static final int DISENO = 1;      // EVALUADO, FICCT, materia 20 / semestre 30 / grupo 40, autor carrera 10
    private static final int REDES = 2;       // EVALUADO, FICCT, materia 21 / semestre 31 / grupo 41, carreras 11 y 20
    private static final int ROBOTICA = 3;    // EVALUADO, otra facultad (2), autor carrera 20
    private static final int APROBADO = 4;    // APROBADO: coincide con "diseno" pero no esta publicado
    private static final int BORRADOR = 5;    // BORRADOR: idem
    private static final int USUARIO_ANA = 501;   // persona 101 (carrera 11), autora de REDES

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;
    @Autowired
    private TrabajoRepository trabajoRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private TrabajoHistorialRepository historialRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GrupoDocenteRepository grupoDocenteRepository;
    @Autowired
    private ConfigFacultadRepository configFacultadRepository;

    private final ParametrosService parametros = mock(ParametrosService.class);
    private final ConfigFacultadService configFacultadService = mock(ConfigFacultadService.class);
    private RepositorioService service;

    @BeforeEach
    void setUp() {
        // configDe (cache Redis) se prueba en ConfigFacultadServiceTest; aqui aplica la misma regla sobre la BD real
        when(configFacultadService.configDe(any())).thenAnswer(inv -> {
            Integer id = inv.getArgument(0);
            boolean acceso = configFacultadRepository.findByFacultadIdFacultad(id).map(ConfigFacultad::getAccesoExterno)
                    .orElseGet(() -> parametros.booleano(Parametro.FACULTAD_ACCESO_EXTERNO));
            return ConfigFacultadMock.con(id, 100, acceso);
        });
        AccesoExternoService accesoExterno = new AccesoExternoService(configFacultadRepository, configFacultadService,
                parametros);
        service = new RepositorioService(trabajoRepository, documentoRepository, historialRepository,
                new AccesoTrabajos(usuarioRepository, trabajoRepository, grupoDocenteRepository), accesoExterno,
                new TrabajoMapperImpl());
        datos();
    }

    // ---------- Migracion V10 ----------

    @Test
    void migracionV10CreaLaConfiguracionEsUnaccentElVectorYElIndiceGin() {
        assertThat(jdbc.queryForObject("select to_tsvector('es_unaccent', 'Diseño de Sistemas')::text", String.class))
                .isEqualTo("'disen':1 'sistem':3");
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'idx_trabajo_busqueda'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns "
                + "where table_name = 'trabajo' and column_name = 'busqueda'", String.class)).isEqualTo("NO");
        // pesos: titulo A, hashtags B, resumen C
        assertThat(jdbc.queryForObject("select busqueda::text from trabajo where id_trabajo = 2", String.class))
                .contains("'analisis':1A", ":4B", "'red':3A,5B", "'epoc':9C");
    }

    @Test
    void laBusquedaUsaElIndiceGin() {
        jdbc.execute("SET LOCAL enable_seqscan = off");
        String plan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT id_trabajo FROM trabajo WHERE f_busqueda_coincide(busqueda, 'redes')", String.class));
        // f_busqueda_coincide se inlinea a "busqueda @@ websearch_to_tsquery(...)": el planner puede usar el GIN
        assertThat(plan).contains("idx_trabajo_busqueda");
    }

    // ---------- Full-text ----------

    @Test
    void sinFiltrosSoloEvaluadosMasRecientesPrimero() {
        PaginaResponse<TrabajoRepositorioResumenResponse> pagina = buscar(FiltrosRepositorio.ninguno());
        assertThat(ids(pagina)).containsExactly(REDES, DISENO, ROBOTICA);
        assertThat(pagina.totalElementos()).isEqualTo(3);
        TrabajoRepositorioResumenResponse redes = pagina.contenido().getFirst();
        assertThat(redes.autores()).containsExactly("Ana Rojas", "Luis Vaca");  // creador primero
        assertThat(redes.periodo()).isEqualTo("1-2026");
        assertThat(redes.siglaMateria()).isEqualTo("INF513");
        assertThat(redes.hashtags()).containsExactly("educación", "redes");
        assertThat(redes.extracto()).isEqualTo("Estudio de la época actual.");
        // fecha de la transicion a EVALUADO del historial; sin historial, fecha_actualizacion
        assertThat(redes.fechaPublicacion()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(pagina.contenido().get(1).fechaPublicacion()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    }

    @Test
    void raicesMayusculasYTildes() {
        assertThat(ids(buscar(q("sistemas")))).containsExactly(DISENO);             // titulo "Sistema": misma raiz
        assertThat(ids(buscar(q("sistema")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("diseno")))).containsExactly(DISENO);               // no APROBADO/BORRADOR
        assertThat(ids(buscar(q("DISEÑOS")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("SISTEMA DE INFORMACIÓN")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("informacion")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("EPOCA")))).containsExactly(REDES);                 // resumen "época"
        assertThat(ids(buscar(q("red")))).containsExactly(REDES);                   // "redes" -> raiz "red"
        assertThat(ids(buscar(q("  robótica  ")))).containsExactly(ROBOTICA);
        assertThat(ids(buscar(q("inexistente")))).isEmpty();
        assertThat(ids(buscar(q("   ")))).containsExactly(REDES, DISENO, ROBOTICA);  // blanco = sin filtro
    }

    @Test
    void hashtagsEntranEnLaBusqueda() {
        assertThat(ids(buscar(q("educacion")))).containsExactly(REDES);             // hashtag "educación"
        assertThat(ids(buscar(q("springboot")))).containsExactly(DISENO);
    }

    @Test
    void frasesEntreComillasYExclusion() {
        assertThat(ids(buscar(q("\"análisis de redes\"")))).containsExactly(REDES);
        assertThat(ids(buscar(q("\"redes de análisis\"")))).isEmpty();             // la frase respeta el orden
        assertThat(ids(buscar(q("sistema -funcional")))).isEmpty();                  // DISENO tiene "funcional"
        assertThat(ids(buscar(q("diseno -redes")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("redes or robotica")))).containsExactlyInAnyOrder(REDES, ROBOTICA);
    }

    @Test
    void rankingTituloPorEncimaDelResumenAunqueSeaMasAntiguo() {
        // trabajo 6: "redes" solo en el resumen y publicado DESPUES que REDES (que la tiene en el titulo)
        jdbc.execute("""
                INSERT INTO trabajo (id_trabajo, titulo, resumen, estado, calificacion, id_persona_creador, id_grupo,
                                     id_materia, id_semestre, id_tipo_trabajo, fecha_creacion, fecha_actualizacion)
                VALUES (6, 'Gestion de inventarios', 'Inventarios con redes neuronales.', 'EVALUADO', 75, 100, 40,
                        20, 30, 60, '2026-09-01T10:00:00Z', '2026-11-20T10:00:00Z');
                INSERT INTO trabajo_autor (id_trabajo, id_persona) VALUES (6, 100);
                """);
        assertThat(ids(buscar(FiltrosRepositorio.ninguno())).getFirst()).isEqualTo(6);  // sin q: fecha
        assertThat(ids(buscar(q("redes")))).containsExactly(REDES, 6);                   // con q: relevancia
    }

    @Test
    void consultasSinPalabrasUtilesOConSimbolosNoFallan() {
        // decision documentada: solo stopwords o solo simbolos -> consulta vacia -> 0 resultados
        assertThat(ids(buscar(q("de la")))).isEmpty();
        assertThat(ids(buscar(q("%")))).isEmpty();
        assertThat(ids(buscar(q("\"")))).isEmpty();
        assertThat(ids(buscar(q("-")))).isEmpty();
        assertThat(ids(buscar(q("!!(& |")))).isEmpty();
        assertThat(ids(buscar(q("x' OR '1'='1")))).isEmpty();  // valor enlazado como parametro, nunca SQL
        assertThat(ids(buscar(q("100%")))).containsExactly(DISENO);  // "100" esta en el resumen
    }

    @Test
    void losTriggersMantienenElVectorAlCambiarTituloYHashtags() {
        // SQL directo (cualquier cliente): titulo y quitar un hashtag
        jdbc.update("UPDATE trabajo SET titulo = 'Compiladores modernos' WHERE id_trabajo = 1");
        jdbc.update("DELETE FROM trabajo_hashtag WHERE id_trabajo = 2 AND id_hashtag = 2");
        em.clear();
        assertThat(ids(buscar(q("compiladores")))).containsExactly(DISENO);
        assertThat(ids(buscar(q("diseno")))).isEmpty();
        assertThat(ids(buscar(q("educacion")))).isEmpty();

        // Por JPA (como lo hace TrabajoService): agregar un hashtag a la coleccion del trabajo
        jdbc.update("INSERT INTO hashtag (id_hashtag, texto) VALUES (9, 'blockchain')");
        Trabajo robotica = trabajoRepository.findById(ROBOTICA).orElseThrow();
        robotica.getHashtags().add(em.find(Hashtag.class, 9));
        em.flush();
        em.clear();
        assertThat(ids(buscar(q("blockchain")))).containsExactly(ROBOTICA);
    }

    // ---------- Filtros ----------

    @Test
    void hashtagExacto() {
        assertThat(ids(buscar(new FiltrosRepositorio(null, "springboot", null, null, null, null, null, null))))
                .containsExactly(DISENO);
        assertThat(ids(buscar(new FiltrosRepositorio(null, " #SpringBoot ", null, null, null, null, null, null))))
                .containsExactly(DISENO);
        assertThat(ids(buscar(new FiltrosRepositorio(null, "spring", null, null, null, null, null, null)))).isEmpty();
    }

    @Test
    void cadaFiltroPorCatalogo() {
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, 41, null, null, null, null, null))))
                .containsExactly(REDES);
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, 1, null, null, null, null))))
                .containsExactly(REDES, DISENO);
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, 2, null, null, null, null))))
                .containsExactly(ROBOTICA);
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, null, null, 20, null, null))))
                .containsExactly(DISENO);
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, null, null, null, 31, null))))
                .containsExactly(REDES);
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, null, null, null, null, 62))))
                .containsExactly(ROBOTICA);
    }

    @Test
    void carreraDeAlgunAutor() {
        assertThat(ids(buscar(carrera(10)))).containsExactly(DISENO);          // APROBADO del mismo autor no sale
        assertThat(ids(buscar(carrera(11)))).containsExactly(REDES);
        assertThat(ids(buscar(carrera(20)))).containsExactly(REDES, ROBOTICA);  // coautor: sin filas duplicadas
        assertThat(ids(buscar(carrera(99)))).isEmpty();
    }

    @Test
    void filtrosCombinados() {
        assertThat(ids(buscar(new FiltrosRepositorio(null, null, null, 1, 20, null, null, null))))
                .containsExactly(REDES);
        assertThat(ids(buscar(new FiltrosRepositorio("analisis", "redes", 41, 1, 11, 21, 31, 61))))
                .containsExactly(REDES);
        // el hashtag es exacto: "educacion" no es "educación" (q si los iguala)
        assertThat(ids(buscar(new FiltrosRepositorio(null, "educacion", null, null, null, null, null, null)))).isEmpty();
        assertThat(ids(buscar(new FiltrosRepositorio("redes", null, null, null, null, null, 30, null)))).isEmpty();
        assertThat(ids(buscar(new FiltrosRepositorio("diseno", null, null, 2, null, null, null, null)))).isEmpty();
        assertThat(ids(buscar(new FiltrosRepositorio("diseno", null, 41, null, null, null, null, null)))).isEmpty();
    }

    @Test
    void paginacionConTotales() {
        PaginaResponse<TrabajoRepositorioResumenResponse> primera = service.buscar(FiltrosRepositorio.ninguno(), 0, 2);
        assertThat(ids(primera)).containsExactly(REDES, DISENO);
        assertThat(primera.totalElementos()).isEqualTo(3);
        assertThat(primera.totalPaginas()).isEqualTo(2);
        PaginaResponse<TrabajoRepositorioResumenResponse> segunda = service.buscar(FiltrosRepositorio.ninguno(), 1, 2);
        assertThat(ids(segunda)).containsExactly(ROBOTICA);
        assertThat(segunda.totalElementos()).isEqualTo(3);

        PaginaResponse<TrabajoRepositorioResumenResponse> conTexto =
                service.buscar(FiltrosRepositorio.texto("redes or robotica"), 0, 1);
        assertThat(conTexto.contenido()).hasSize(1);
        assertThat(conTexto.totalElementos()).isEqualTo(2);  // el count no se ordena ni duplica filas
    }

    @Test
    void sinNMasUnoConYSinTexto() {
        for (FiltrosRepositorio filtros : new FiltrosRepositorio[]{FiltrosRepositorio.ninguno(),
                FiltrosRepositorio.texto("redes or diseno")}) {
            em.clear();
            Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            estadisticas.clear();
            PaginaResponse<TrabajoRepositorioResumenResponse> pagina = service.buscar(filtros, 0, 2);
            assertThat(pagina.contenido()).hasSize(2).allSatisfy(t -> {
                assertThat(t.autores()).isNotEmpty();
                assertThat(t.hashtags()).isNotEmpty();
            });
            // pagina (con materia, semestre, tipo y el vector en un join) + count + autores en lote
            // + hashtags en lote + fechas de publicacion
            assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        }
    }

    // ---------- Ficha ----------

    @Test
    void fichaPublicaConAutorActualHashtagsDocumentosYFechaDePublicacion() {
        em.clear();
        TrabajoPublicoResponse ficha = service.obtener(jwt(USUARIO_ANA), REDES);
        assertThat(ficha.titulo()).isEqualTo("Análisis de redes");
        assertThat(ficha.idFacultad()).isEqualTo(1);
        assertThat(ficha.tipoTrabajo()).isEqualTo("Monografia");
        assertThat(ficha.autores()).extracting(AutorResponse::nombre, AutorResponse::esUsuarioActual)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Ana", true),
                        org.assertj.core.groups.Tuple.tuple("Luis", false));
        assertThat(ficha.hashtags()).containsExactly("educación", "redes");
        assertThat(ficha.documentos()).singleElement()
                .satisfies(d -> assertThat(d.nombreArchivo()).isEqualTo("informe.pdf"));
        // la transicion a EVALUADO del historial manda sobre fecha_actualizacion
        assertThat(ficha.fechaPublicacion()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));

        TrabajoPublicoResponse sinHistorial = service.obtener(jwt(USUARIO_ANA), DISENO);
        assertThat(sinHistorial.fechaPublicacion()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(sinHistorial.autores()).noneMatch(AutorResponse::esUsuarioActual);
    }

    @Test
    void fichaDeTrabajoNoEvaluadoOInexistenteEs404() {
        for (int id : new int[]{APROBADO, BORRADOR, 999}) {
            assertThatThrownBy(() -> service.obtener(jwt(USUARIO_ANA), id))
                    .isInstanceOf(NegocioException.class)
                    .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ---------- Acceso anonimo ----------

    @Test
    void facultadesHabilitadasSegunConfigPropiaYDefault() {
        AccesoExternoService accesoExterno = new AccesoExternoService(configFacultadRepository, configFacultadService,
                parametros);
        assertThat(accesoExterno.facultadesHabilitadas()).isEmpty();  // sin filas propias y default false
        jdbc.update("INSERT INTO facultad (id_facultad, nombre, sigla) VALUES (3, 'FCS', 'FCS')");
        configFacultad(1, true);
        configFacultad(3, false);
        assertThat(accesoExterno.facultadesHabilitadas()).containsExactly(1);
        // default true: se suman las facultades SIN fila propia (la 2); la 3 manda su propio false
        when(parametros.booleano(Parametro.FACULTAD_ACCESO_EXTERNO)).thenReturn(true);
        assertThat(accesoExterno.facultadesHabilitadas()).containsExactly(1, 2);
    }

    @Test
    void busquedaAnonimaSoloDeFacultadesConAccesoExterno() {
        assertThatThrownBy(() -> service.buscarPublico(FiltrosRepositorio.ninguno(), 0, 20))
                .isInstanceOf(NegocioException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);  // ninguna habilitada

        configFacultad(1, true);
        assertThat(ids(service.buscarPublico(FiltrosRepositorio.ninguno(), 0, 20))).containsExactly(REDES, DISENO);
        assertThat(ids(service.buscarPublico(q("robotica"), 0, 20))).isEmpty();      // facultad 2 cerrada
        assertThat(ids(service.buscarPublico(q("sistemas"), 0, 20))).containsExactly(DISENO);
        assertThat(ids(service.buscarPublico(new FiltrosRepositorio(null, null, null, 2, null, null, null, null),
                0, 20))).isEmpty();
        assertThat(service.estadoPublico().facultades()).containsExactly(1);
    }

    @Test
    void fichaAnonimaEs404SiLaFacultadNoPermiteYNuncaMarcaUsuarioActual() {
        configFacultad(1, true);
        em.clear();
        TrabajoPublicoResponse ficha = service.obtenerPublico(REDES);
        assertThat(ficha.autores()).noneMatch(AutorResponse::esUsuarioActual);
        assertThat(ficha.documentos()).hasSize(1);
        for (int id : new int[]{ROBOTICA, APROBADO, 999}) {
            assertThatThrownBy(() -> service.obtenerPublico(id))
                    .isInstanceOf(NegocioException.class)
                    .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ---------- helpers ----------

    private PaginaResponse<TrabajoRepositorioResumenResponse> buscar(FiltrosRepositorio filtros) {
        return service.buscar(filtros, 0, 20);
    }

    private static FiltrosRepositorio q(String q) {
        return FiltrosRepositorio.texto(q);
    }

    private static FiltrosRepositorio carrera(int idCarrera) {
        return new FiltrosRepositorio(null, null, null, null, idCarrera, null, null, null);
    }

    private static List<Integer> ids(PaginaResponse<TrabajoRepositorioResumenResponse> pagina) {
        return pagina.contenido().stream().map(TrabajoRepositorioResumenResponse::idTrabajo).toList();
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }

    private void configFacultad(int idFacultad, boolean accesoExterno) {
        jdbc.update("INSERT INTO config_facultad (id_facultad, acceso_externo) VALUES (?, ?)", idFacultad, accesoExterno);
    }

    /** Catalogos de dos facultades, cuatro personas y los trabajos de prueba (SQL directo: no hay transicion a EVALUADO). */
    private void datos() {
        jdbc.execute("""
                INSERT INTO facultad (id_facultad, nombre, sigla) VALUES (1, 'FICCT', 'FICCT'), (2, 'FCET', 'FCET');
                INSERT INTO carrera (id_carrera, nombre, sigla, id_facultad) VALUES
                    (10, 'Informatica', 'INF', 1), (11, 'Sistemas', 'SIS', 1), (20, 'Economia', 'ECO', 2);
                INSERT INTO persona (id_persona, nombre, apellidos, ci, correo, registro, id_carrera) VALUES
                    (100, 'Juan', 'Perez', 1000, 'juan@uagrm.edu.bo', 219000100, 10),
                    (101, 'Ana', 'Rojas', 1001, 'ana@uagrm.edu.bo', 219000101, 11),
                    (102, 'Luis', 'Vaca', 1002, 'luis@uagrm.edu.bo', 219000102, 20);
                INSERT INTO usuario (id_usuario, usuario, password, requiere_cambio_password, id_tipo_usuario, id_persona)
                    SELECT 500 + p.id_persona - 100, p.registro::text, 'x', false, t.id_tipo_usuario, p.id_persona
                    FROM persona p, tipo_usuario t WHERE t.nombre = 'ESTUDIANTE' AND p.id_persona IN (100, 101, 102);
                INSERT INTO semestre (id_semestre, semestre, anio, id_facultad) VALUES
                    (30, 2, 2026, 1), (31, 1, 2026, 1), (32, 2, 2026, 2);
                INSERT INTO materia (id_materia, nombre, sigla, id_facultad) VALUES
                    (20, 'Ingenieria de Software I', 'INF412', 1), (21, 'Redes II', 'INF513', 1),
                    (22, 'Robotica', 'ECO100', 2);
                INSERT INTO grupo (id_grupo, nombre, id_materia, id_semestre) VALUES
                    (40, 'SA', 20, 30), (41, 'SB', 21, 31), (42, 'SC', 22, 32);
                INSERT INTO tipo_trabajo (id_tipo_trabajo, nombre, id_facultad) VALUES
                    (60, 'Proyecto', 1), (61, 'Monografia', 1), (62, 'Proyecto', 2);
                INSERT INTO fase (id_fase, nombre, id_facultad) VALUES (50, 'Perfil', 1);
                INSERT INTO hashtag (id_hashtag, texto) VALUES
                    (1, 'springboot'), (2, 'educación'), (3, 'redes'), (4, 'ia');
                INSERT INTO trabajo (id_trabajo, titulo, resumen, estado, calificacion, observaciones, id_persona_creador,
                                     id_grupo, id_materia, id_semestre, id_tipo_trabajo, fecha_creacion,
                                     fecha_actualizacion) VALUES
                    (1, 'Diseño de un Sistema de Información', 'Sistema al 100% funcional.', 'EVALUADO', 90, 'Muy bien',
                        100, 40, 20, 30, 60, '2026-09-01T10:00:00Z', '2026-10-01T10:00:00Z'),
                    (2, 'Análisis de redes', 'Estudio de la época actual.', 'EVALUADO', 80, null,
                        101, 41, 21, 31, 61, '2026-09-01T10:00:00Z', '2026-11-03T10:00:00Z'),
                    (3, 'Robótica móvil', 'Control de motores.', 'EVALUADO', 70, null,
                        102, 42, 22, 32, 62, '2026-09-01T10:00:00Z', '2026-09-15T10:00:00Z'),
                    (4, 'Diseño de compiladores', 'Otro diseño.', 'APROBADO', null, null,
                        100, 40, 20, 30, 60, '2026-09-01T10:00:00Z', '2026-12-01T10:00:00Z'),
                    (5, 'Diseño borrador', 'Borrador.', 'BORRADOR', null, null,
                        100, 40, 20, 30, 60, '2026-09-01T10:00:00Z', '2026-12-02T10:00:00Z');
                INSERT INTO trabajo_autor (id_trabajo, id_persona) VALUES
                    (1, 100), (2, 101), (2, 102), (3, 102), (4, 100), (5, 100);
                INSERT INTO trabajo_hashtag (id_trabajo, id_hashtag) VALUES (1, 1), (2, 2), (2, 3), (3, 4), (4, 1);
                INSERT INTO documento (id_documento, id_trabajo, id_fase, titulo, tipo, categoria, nombre_archivo, extension,
                                       content_type, ruta_archivo, tamano_bytes, sha256) VALUES
                    (70, 2, 50, 'Informe', 'ARCHIVO', 'DOCUMENTO', 'informe.pdf', 'pdf', 'application/pdf',
                        'trabajos/2/a.pdf', 10, repeat('a', 64));
                INSERT INTO trabajo_historial (id_trabajo, estado_anterior, estado_nuevo, id_usuario, fecha) VALUES
                    (2, 'APROBADO', 'EVALUADO', 501, '2026-11-02T10:00:00Z');
                """);
    }
}
