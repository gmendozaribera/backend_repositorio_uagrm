package com.uagrm.repositorio.imp.service.padron;

import com.uagrm.repositorio.imp.exception.NegocioException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Lee el archivo del padron (.csv, .xlsx o .xls) y devuelve sus filas de datos sin validar.
 * Cualquier problema con el archivo en si (extension, contenido que no corresponde a la extension, cabecera
 * incompleta, vacio, demasiadas filas) es un 400 y no se importa nada. Los errores de cada fila los valida
 * despues {@link PadronService}.
 */
@Component
public class LectorPadron {

    public static final String REGISTRO = "registro";
    public static final String CI = "ci";
    public static final String CORREO = "correo";
    public static final String PASSWORD_DEFAULT = "password_default";
    public static final String NOMBRES = "nombres";
    public static final String APELLIDOS = "apellidos";
    public static final String SIGLA_CARRERA = "sigla_carrera";
    public static final List<String> COLUMNAS =
            List.of(REGISTRO, CI, CORREO, PASSWORD_DEFAULT, NOMBRES, APELLIDOS, SIGLA_CARRERA);

    public static final int MAX_FILAS = 10_000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final char BOM = '﻿';

    private enum Formato { CSV, XLSX, XLS }

    public List<FilaPadron> leer(String nombreArchivo, byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw invalido("El archivo esta vacio.");
        }
        if (contenido.length > MAX_BYTES) {
            throw invalido("El archivo supera el tamano maximo de 5 MB.");
        }
        List<FilaPadron> filas = switch (formato(nombreArchivo)) {
            case CSV -> leerCsv(contenido);
            case XLSX -> leerExcel(contenido, FileMagic.OOXML, ".xlsx");
            case XLS -> leerExcel(contenido, FileMagic.OLE2, ".xls");
        };
        if (filas.isEmpty()) {
            throw invalido("El archivo no tiene filas de datos debajo de la cabecera.");
        }
        return filas;
    }

    // ---------- CSV ----------

    private List<FilaPadron> leerCsv(byte[] contenido) {
        FileMagic magia = FileMagic.valueOf(contenido);
        if (magia == FileMagic.OLE2 || magia == FileMagic.OOXML || magia == FileMagic.PDF) {
            throw invalido("El contenido del archivo no corresponde a un CSV de texto.");
        }
        String texto = utf8(contenido);
        if (texto.indexOf('\0') >= 0) {
            throw invalido("El contenido del archivo no corresponde a un CSV de texto.");
        }
        if (!texto.isEmpty() && texto.charAt(0) == BOM) {
            texto = texto.substring(1);
        }
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(separador(texto))
                .setIgnoreEmptyLines(false)  // asi el numero de registro coincide con la fila del archivo
                .get();
        List<FilaPadron> filas = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(texto), formato)) {
            Map<Integer, String> cabecera = null;
            for (CSVRecord registro : parser) {
                List<String> celdas = registro.toList();
                int fila = (int) registro.getRecordNumber();
                if (cabecera == null) {
                    cabecera = cabecera(celdas);
                    continue;
                }
                agregar(filas, fila, cabecera, celdas);
            }
            if (cabecera == null) {
                throw invalido("El archivo esta vacio.");
            }
        } catch (IOException | UncheckedIOException | IllegalStateException ex) {
            throw invalido("El CSV esta mal formado (revise las comillas): " + ex.getMessage());
        }
        return filas;
    }

    private static String utf8(byte[] contenido) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw invalido("El archivo CSV debe estar codificado en UTF-8.");
        }
    }

    /** Punto y coma (Excel en espanol) o coma: el que mas aparece en la primera linea. */
    private static char separador(String texto) {
        int fin = texto.indexOf('\n');
        String primera = fin < 0 ? texto : texto.substring(0, fin);
        long comas = primera.chars().filter(c -> c == ',').count();
        long puntoYComa = primera.chars().filter(c -> c == ';').count();
        return puntoYComa > comas ? ';' : ',';
    }

    // ---------- Excel ----------

    private List<FilaPadron> leerExcel(byte[] contenido, FileMagic esperado, String extension) {
        if (FileMagic.valueOf(contenido) != esperado) {
            throw invalido("El contenido del archivo no corresponde a un Excel " + extension + ".");
        }
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            if (libro.getNumberOfSheets() == 0) {
                throw invalido("El archivo Excel no tiene hojas.");
            }
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formateador = new DataFormatter(Locale.ROOT);
            Row primera = hoja.getRow(hoja.getFirstRowNum());
            if (primera == null || hoja.getPhysicalNumberOfRows() == 0) {
                throw invalido("El archivo esta vacio.");
            }
            Map<Integer, String> cabecera = cabecera(celdas(primera, formateador));
            List<FilaPadron> filas = new ArrayList<>();
            for (int i = primera.getRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila != null) {
                    agregar(filas, i + 1, cabecera, celdas(fila, formateador));
                }
            }
            return filas;
        } catch (NegocioException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalido("No se pudo leer el archivo Excel " + extension + ": esta danado o protegido con contrasena.");
        }
    }

    private static List<String> celdas(Row fila, DataFormatter formateador) {
        List<String> celdas = new ArrayList<>();
        for (int c = 0; c < Math.max(fila.getLastCellNum(), 0); c++) {
            celdas.add(texto(fila.getCell(c), formateador));
        }
        return celdas;
    }

    /** Los numeros enteros (registro, CI, password numerica) se leen sin decimales ni notacion cientifica. */
    private static String texto(Cell celda, DataFormatter formateador) {
        if (celda == null) {
            return "";
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        return switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(celda)
                    ? formateador.formatCellValue(celda)
                    : BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(celda.getBooleanCellValue());
            default -> "";
        };
    }

    // ---------- comun ----------

    /** Columna -> nombre normalizado. Las 7 columnas son obligatorias, en cualquier orden; se ignoran las demas. */
    private static Map<Integer, String> cabecera(List<String> celdas) {
        Map<Integer, String> cabecera = new LinkedHashMap<>();
        Map<String, Integer> vistas = new HashMap<>();
        for (int i = 0; i < celdas.size(); i++) {
            String nombre = celdas.get(i).replace(String.valueOf(BOM), "").trim().toLowerCase(Locale.ROOT);
            if (!COLUMNAS.contains(nombre)) {
                continue;
            }
            if (vistas.putIfAbsent(nombre, i) != null) {
                throw invalido("La columna " + nombre + " esta repetida en la cabecera.");
            }
            cabecera.put(i, nombre);
        }
        List<String> faltantes = COLUMNAS.stream().filter(c -> !vistas.containsKey(c)).toList();
        if (!faltantes.isEmpty()) {
            throw invalido("La primera fila debe ser la cabecera con las columnas " + String.join(", ", COLUMNAS)
                    + ". Faltan: " + String.join(", ", faltantes) + ".");
        }
        return cabecera;
    }

    /** Ignora las filas vacias; corta con 400 si el archivo supera el maximo de filas. */
    private static void agregar(List<FilaPadron> filas, int numero, Map<Integer, String> cabecera, List<String> celdas) {
        Map<String, String> valores = new HashMap<>();
        boolean vacia = true;
        for (Map.Entry<Integer, String> columna : cabecera.entrySet()) {
            String valor = columna.getKey() < celdas.size() ? celdas.get(columna.getKey()).trim() : "";
            vacia &= valor.isEmpty();
            valores.put(columna.getValue(), valor);
        }
        if (vacia) {
            return;
        }
        if (filas.size() >= MAX_FILAS) {
            throw invalido("El archivo supera el maximo de " + MAX_FILAS + " filas.");
        }
        filas.add(new FilaPadron(numero, valores));
    }

    private static Formato formato(String nombreArchivo) {
        String nombre = nombreArchivo == null ? "" : nombreArchivo.trim().toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".csv")) {
            return Formato.CSV;
        }
        if (nombre.endsWith(".xlsx")) {
            return Formato.XLSX;
        }
        if (nombre.endsWith(".xls")) {
            return Formato.XLS;
        }
        throw invalido("Formato no soportado: el archivo debe ser .csv, .xlsx o .xls.");
    }

    private static NegocioException invalido(String detalle) {
        return new NegocioException(HttpStatus.BAD_REQUEST, "Archivo invalido", detalle);
    }
}
