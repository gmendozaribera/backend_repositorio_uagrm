package com.uagrm.repositorio.imp.service.padron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.repository.EstudianteHabilitadoRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dto.mapper.PadronMapperImpl;
import com.uagrm.repositorio.dto.padron.ErrorImportacion;
import com.uagrm.repositorio.dto.padron.ResultadoImportacion;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.PadronMock;
import jakarta.validation.Validation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Importacion del padron: lectura de CSV/Excel (LectorPadron real) y validacion + upsert (repos mockeados). */
class PadronServiceTest {

    private final EstudianteHabilitadoRepository padronRepository = mock(EstudianteHabilitadoRepository.class);
    private final PersonaRepository personaRepository = mock(PersonaRepository.class);
    private final CarreraRepository carreraRepository = mock(CarreraRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private PadronService service;
    private Carrera inf;

    @BeforeEach
    void setUp() {
        service = new PadronService(padronRepository, personaRepository, carreraRepository, passwordEncoder,
                new LectorPadron(), new PadronMapperImpl(),
                Validation.buildDefaultValidatorFactory().getValidator());
        inf = CatalogoMock.carrera(CatalogoMock.facultad());
        Carrera sis = CatalogoMock.carrera(CatalogoMock.facultad());
        sis.setIdCarrera(11);
        sis.setSigla("SIS");
        sis.setEstado(Estado.INACTIVO);
        when(carreraRepository.findAll()).thenReturn(List.of(inf, sis));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "$2a$10$hash-" + inv.getArgument(0));
    }

    // ---------- formatos ----------

    @Test
    void csvConPuntoYComaYBomUtf8() {
        String csv = "﻿Registro;CI;Correo;Password_Default;Nombres;Apellidos;Sigla_Carrera\r\n"
                + "219012345;7654321;Juan.Perez@uagrm.edu.bo;Uagrm2026;Juan Carlos;Perez Lopez;inf\r\n"
                + ";;;;;;\r\n"
                + "219012346;7654322;ana@uagrm.edu.bo;Uagrm2026;Ana;Rios;INF\r\n";

        ResultadoImportacion resultado = importar("padron.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.totalFilas()).isEqualTo(2);
        assertThat(resultado.insertados()).isEqualTo(2);
        assertThat(resultado.conErrores()).isZero();
        List<EstudianteHabilitado> guardados = guardados();
        assertThat(guardados).extracting(EstudianteHabilitado::getRegistro).containsExactly(219012345, 219012346);
        EstudianteHabilitado juan = guardados.get(0);
        assertThat(juan.getCorreo()).isEqualTo("juan.perez@uagrm.edu.bo");
        assertThat(juan.getNombres()).isEqualTo("Juan Carlos");
        assertThat(juan.getCarrera()).isSameAs(inf);
        assertThat(juan.getHabilitado()).isTrue();
        assertThat(juan.getPasswordDefault()).isEqualTo("$2a$10$hash-Uagrm2026");
        assertThat(juan.getFechaImportacion()).isNotNull();
    }

    @Test
    void csvConComaYColumnasEnOtroOrden() {
        String csv = "sigla_carrera,apellidos,nombres,password_default,correo,ci,registro,extra\n"
                + "INF,\"Perez, Lopez\",Juan,Uagrm2026,juan@uagrm.edu.bo,7654321,219012345,ignorada\n";

        ResultadoImportacion resultado = importar("padron.CSV", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.insertados()).isEqualTo(1);
        assertThat(guardados().get(0).getApellidos()).isEqualTo("Perez, Lopez");
    }

    @Test
    void xlsxGeneradoEnMemoriaConNumerosComoCeldasNumericas() throws IOException {
        byte[] xlsx = excel(new XSSFWorkbook(), new Object[][]{
                {"registro", "ci", "correo", "password_default", "nombres", "apellidos", "sigla_carrera"},
                {219012345d, 7654321d, "juan@uagrm.edu.bo", 123456d, "Juan", "Perez", "INF"},
                {219012346d, 7654322d, "ana@uagrm.edu.bo", 123456d, "Ana", "Rios", "XYZ"}});

        ResultadoImportacion resultado = importar("padron.xlsx", xlsx);

        assertThat(resultado.totalFilas()).isEqualTo(2);
        assertThat(resultado.insertados()).isEqualTo(1);
        assertThat(resultado.conErrores()).isEqualTo(1);
        assertThat(resultado.errores()).containsExactly(
                new ErrorImportacion(3, "sigla_carrera", "No existe una carrera activa con la sigla XYZ."));
        EstudianteHabilitado juan = guardados().get(0);
        assertThat(juan.getRegistro()).isEqualTo(219012345);
        assertThat(juan.getPasswordDefault()).isEqualTo("$2a$10$hash-123456");
    }

    @Test
    void xlsAntiguo() throws IOException {
        byte[] xls = excel(new HSSFWorkbook(), new Object[][]{
                {"REGISTRO", "CI", "CORREO", "PASSWORD_DEFAULT", "NOMBRES", "APELLIDOS", "SIGLA_CARRERA"},
                {219012345d, 7654321d, "juan@uagrm.edu.bo", "Uagrm2026", "Juan", "Perez", "INF"}});

        assertThat(importar("padron.xls", xls).insertados()).isEqualTo(1);
    }

    // ---------- archivo invalido: 400 sin importar nada ----------

    @Test
    void extensionNoSoportadaDevuelve400() {
        esperar400("padron.pdf", (PadronMock.CABECERA + "\n").getBytes(StandardCharsets.UTF_8), "Formato no soportado");
    }

    @Test
    void contenidoQueNoCorrespondeALaExtensionDevuelve400() throws IOException {
        esperar400("padron.xlsx", (PadronMock.CABECERA + "\n1,2,a@b.co,1234,A,B,INF").getBytes(StandardCharsets.UTF_8),
                "no corresponde a un Excel .xlsx");
        byte[] xlsx = excel(new XSSFWorkbook(), new Object[][]{{"registro"}});
        esperar400("padron.csv", xlsx, "no corresponde a un CSV");
    }

    @Test
    void cabeceraIncompletaDevuelve400() {
        esperar400("padron.csv", "registro,ci,correo\n1,2,a@b.co".getBytes(StandardCharsets.UTF_8),
                "Faltan: password_default, nombres, apellidos, sigla_carrera");
    }

    @Test
    void archivoVacioOSoloCabeceraDevuelve400() {
        esperar400("padron.csv", new byte[0], "vacio");
        esperar400("padron.csv", (PadronMock.CABECERA + "\n").getBytes(StandardCharsets.UTF_8), "no tiene filas");
    }

    @Test
    void csvQueNoEsUtf8Devuelve400() {
        byte[] latin1 = (PadronMock.CABECERA + "\n1,2,a@b.co,1234,Jose,Nuñez,INF").getBytes(StandardCharsets.ISO_8859_1);
        esperar400("padron.csv", latin1, "UTF-8");
    }

    @Test
    void masDeDiezMilFilasDevuelve400() {
        StringBuilder csv = new StringBuilder(PadronMock.CABECERA).append('\n');
        for (int i = 1; i <= LectorPadron.MAX_FILAS + 1; i++) {
            csv.append(i).append(',').append(i).append(",a@b.co,1234,A,B,INF\n");
        }
        esperar400("padron.csv", csv.toString().getBytes(StandardCharsets.UTF_8), "10000 filas");
    }

    // ---------- errores por fila ----------

    @Test
    void cadaFilaSeValidaPorSeparadoYNoInterrumpeLaImportacion() {
        String csv = PadronMock.CABECERA + "\n"
                + "219012345,7654321,juan@uagrm.edu.bo,Uagrm2026,Juan,Perez,INF\n"   // 2: ok
                + "abc,-5,no-es-correo,123,,Rios,SIS\n"                                  // 3: varios errores
                + "219012345,7654399,otro@uagrm.edu.bo,Uagrm2026,Otro,Perez,INF\n"   // 4: registro repetido
                + "219012347,7654321,ana@uagrm.edu.bo,Uagrm2026,Ana,Rios,INF\n";     // 5: ci repetido

        ResultadoImportacion resultado = importar("padron.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.totalFilas()).isEqualTo(4);
        assertThat(resultado.insertados()).isEqualTo(1);
        assertThat(resultado.conErrores()).isEqualTo(3);
        assertThat(resultado.errores()).extracting(ErrorImportacion::fila, ErrorImportacion::columna).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3, "registro"),
                org.assertj.core.groups.Tuple.tuple(3, "ci"),
                org.assertj.core.groups.Tuple.tuple(3, "correo"),
                org.assertj.core.groups.Tuple.tuple(3, "password_default"),
                org.assertj.core.groups.Tuple.tuple(3, "nombres"),
                org.assertj.core.groups.Tuple.tuple(3, "sigla_carrera"),
                org.assertj.core.groups.Tuple.tuple(4, "registro"),
                org.assertj.core.groups.Tuple.tuple(5, "ci"));
        assertThat(resultado.errores().get(6).mensaje()).contains("repetido en el archivo (fila 2)");
    }

    @Test
    void upsertPorRegistroYCiDeOtroRegistroEnBd() {
        EstudianteHabilitado existente = PadronMock.estudianteHabilitado(inf, "$2a$10$viejo");
        existente.setHabilitado(false);
        EstudianteHabilitado otro = PadronMock.estudianteHabilitado(inf, "$2a$10$viejo");
        otro.setRegistro(219000999);
        otro.setCi(5550001);
        when(padronRepository.findByRegistroIn(anyCollection())).thenReturn(List.of(existente));
        when(padronRepository.findByCiIn(anyCollection())).thenReturn(List.of(existente, otro));
        Persona docente = new Persona();
        docente.setCi(5550002);
        when(personaRepository.findByCiIn(anyCollection())).thenReturn(List.of(docente));

        String csv = PadronMock.CABECERA + "\n"
                + PadronMock.REGISTRO + "," + PadronMock.CI + ",nuevo@uagrm.edu.bo,Nueva2026,Juan,Perez,INF\n"
                + "219012399,5550001,x@uagrm.edu.bo,Uagrm2026,X,Y,INF\n"
                + "219012398,5550002,z@uagrm.edu.bo,Uagrm2026,Z,W,INF\n";

        ResultadoImportacion resultado = importar("padron.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.actualizados()).isEqualTo(1);
        assertThat(resultado.insertados()).isZero();
        assertThat(resultado.errores()).extracting(ErrorImportacion::mensaje).containsExactly(
                "El CI 5550001 ya pertenece al registro 219000999 del padron.",
                "El CI 5550002 ya pertenece a otra cuenta del sistema.");
        assertThat(existente.getCorreo()).isEqualTo("nuevo@uagrm.edu.bo");
        assertThat(existente.getPasswordDefault()).isEqualTo("$2a$10$hash-Nueva2026");
        assertThat(existente.getHabilitado()).isTrue();
    }

    @Test
    void bcryptSeCalculaUnaSolaVezPorPasswordDistinta() {
        StringBuilder csv = new StringBuilder(PadronMock.CABECERA).append('\n');
        for (int i = 1; i <= 50; i++) {
            csv.append(219000000 + i).append(',').append(7000000 + i).append(",e").append(i)
                    .append("@uagrm.edu.bo,").append(i == 50 ? "Distinta1" : "Uagrm2026").append(",N,A,INF\n");
        }

        ResultadoImportacion resultado = importar("padron.csv", csv.toString().getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.insertados()).isEqualTo(50);
        verify(passwordEncoder, times(1)).encode("Uagrm2026");
        verify(passwordEncoder, times(1)).encode("Distinta1");
        verify(passwordEncoder, times(2)).encode(anyString());
    }

    // ---------- helpers ----------

    private ResultadoImportacion importar(String nombre, byte[] contenido) {
        return service.importar(new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido));
    }

    private void esperar400(String nombre, byte[] contenido, String detalle) {
        assertThatThrownBy(() -> importar(nombre, contenido))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains(detalle);
                });
        verify(padronRepository, times(0)).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<EstudianteHabilitado> guardados() {
        ArgumentCaptor<Iterable<EstudianteHabilitado>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(padronRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        List<EstudianteHabilitado> todos = new ArrayList<>();
        captor.getAllValues().forEach(lote -> lote.forEach(todos::add));
        return todos;
    }

    private static byte[] excel(Workbook libro, Object[][] filas) throws IOException {
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Padron");
            for (int i = 0; i < filas.length; i++) {
                Row fila = hoja.createRow(i);
                for (int j = 0; j < filas[i].length; j++) {
                    Object valor = filas[i][j];
                    if (valor instanceof Double numero) {
                        fila.createCell(j).setCellValue(numero);
                    } else {
                        fila.createCell(j).setCellValue((String) valor);
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
