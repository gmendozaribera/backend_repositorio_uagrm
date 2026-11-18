package com.uagrm.repositorio.imp.service.documento;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valida un archivo subido ANTES de guardarlo. En terminos simples: no se cree lo que dice el archivo de si mismo.
 * <ol>
 *   <li><b>Extension</b> en la lista permitida de la categoria (DOCUMENTO: pdf/docx/odt...).</li>
 *   <li><b>Tamano</b>: VIDEO usa WORK_GROUP.VIDEO_MAX_MB; el resto, el limite de la facultad (lo calcula quien
 *       llama). Se valida con el tamano declarado por el multipart, sin leer el contenido.</li>
 *   <li><b>Tipo real</b> con Apache Tika: se leen los primeros bytes ("magic bytes", la firma que cada formato
 *       tiene al principio) y deben corresponder a la extension. Asi un programa .exe renombrado a .pdf se rechaza:
 *       su contenido empieza con "MZ", no con "%PDF". El Content-Type que manda el navegador se ignora (lo
 *       controla quien sube el archivo).</li>
 *   <li><b>SVG</b>: es una imagen escrita en XML que PUEDE llevar codigo JavaScript ({@code <script>}, atributos
 *       {@code onload=}, enlaces {@code javascript:}). Si alguien la abriera directamente desde nuestro dominio,
 *       ese codigo correria con la sesion de quien la mira (ataque XSS: robar su sesion o actuar en su nombre).
 *       Se rechaza cualquier SVG con esas construcciones (o con entidades XML, que permiten disfrazarlas).
 *       Ademas la descarga siempre va como adjunto con nosniff, asi que el navegador nunca la ejecuta.</li>
 *   <li><b>Nombre</b>: se guarda solo para mostrarlo (sin rutas, sin caracteres de control, maximo 200); el objeto
 *       en MinIO usa un UUID, nunca el nombre original (asi nadie puede pisar ni adivinar archivos ajenos).</li>
 * </ol>
 * El contenido se lee en streaming (solo los primeros KB para el tipo; el SVG por bloques): nunca se carga un
 * archivo entero en memoria.
 */
@Slf4j
@Component
public class ValidadorArchivos {

    static final String CAMPO = "archivo";
    static final int LARGO_MAXIMO_NOMBRE = 200;
    private static final long BYTES_POR_MB = 1024L * 1024L;

    /**
     * Construcciones activas de un SVG: etiqueta script, atributos de evento (onload=, onclick=...), URLs
     * javascript: y declaraciones de entidades XML (permiten armar las anteriores sin escribirlas literalmente).
     */
    private static final Pattern SVG_PELIGROSO = Pattern.compile(
            "<\\s*script|[\\s/\"']on[a-z]+\\s*=|javascript\\s*:|<!ENTITY", Pattern.CASE_INSENSITIVE);
    /** Lo que se conserva del bloque anterior al leer el SVG por partes (una coincidencia puede quedar partida). */
    private static final int SOLAPAMIENTO = 64;

    /** Base de tipos de tika-core: solo firmas (magic bytes), sin el nombre del archivo. */
    private final MimeTypes tipos = MimeTypes.getDefaultMimeTypes();

    /** Resultado: nombre saneado, extension (minusculas) y Content-Type canonico para guardar y descargar. */
    public record ArchivoValidado(String nombre, String extension, String contentType) {
    }

    /**
     * @param tamanoMaximoMB limite vigente para esta categoria y facultad
     * @throws NegocioException 400 con errores.archivo si algo no cumple
     */
    public ArchivoValidado validar(MultipartFile archivo, CategoriaDocumento categoria, int tamanoMaximoMB) {
        String nombre = sanearNombre(archivo.getOriginalFilename());
        String extension = extension(nombre);
        List<String> permitidas = ReglasDocumentos.EXTENSIONES.get(categoria);
        if (extension == null || !permitidas.contains(extension)) {
            throw invalido("formato no permitido para " + categoria + " (permitidos: " + String.join(", ", permitidas)
                    + ")", "El formato del archivo no esta permitido para la categoria " + categoria + ".");
        }
        if (archivo.isEmpty()) {
            throw invalido("el archivo esta vacio", "El archivo esta vacio.");
        }
        if (archivo.getSize() > tamanoMaximoMB * BYTES_POR_MB) {
            throw invalido("supera el tamano maximo de " + tamanoMaximoMB + " MB",
                    "El archivo supera el tamano maximo de " + tamanoMaximoMB + " MB"
                            + (categoria == CategoriaDocumento.VIDEO ? " para videos." : " de la facultad."));
        }
        String detectado = detectarTipo(archivo);
        if (!ReglasDocumentos.TIPOS_REALES.get(extension).contains(detectado)) {
            log.warn("Archivo rechazado: extension {} pero el contenido es {}", extension, detectado);
            throw invalido("el contenido no corresponde a un archivo ." + extension,
                    "El contenido del archivo no corresponde a su extension ." + extension + ".");
        }
        if ("svg".equals(extension)) {
            verificarSvg(archivo);
        }
        return new ArchivoValidado(nombre, extension, ReglasDocumentos.CONTENT_TYPE.get(extension));
    }

    /**
     * Nombre para mostrar: sin carpetas (lo que viene antes de la ultima / o \), sin caracteres de control ni
     * comillas, recortado a 200 conservando la extension. Vacio → "archivo".
     */
    static String sanearNombre(String original) {
        String nombre = original == null ? "" : original;
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        nombre = nombre.replaceAll("[\\p{Cntrl}\"]", "").strip();
        while (nombre.startsWith(".")) {
            nombre = nombre.substring(1);  // sin nombres ocultos ni "..": no son rutas, pero confunden
        }
        if (nombre.isEmpty()) {
            return "archivo";
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            int punto = nombre.lastIndexOf('.');
            String ext = punto > 0 && nombre.length() - punto <= 11 ? nombre.substring(punto) : "";
            nombre = nombre.substring(0, LARGO_MAXIMO_NOMBRE - ext.length()) + ext;
        }
        return nombre;
    }

    /** Extension en minusculas o null si no tiene. */
    static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            return null;
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /** Tipo base detectado solo por el contenido (no se pasa el nombre: Tika le daria prioridad a la extension). */
    private String detectarTipo(MultipartFile archivo) {
        try (InputStream in = new BufferedInputStream(archivo.getInputStream())) {
            MediaType tipo = tipos.detect(in, new Metadata());
            return tipo.getBaseType().toString();
        } catch (IOException ex) {
            throw invalido("no se pudo leer el archivo", "No se pudo leer el archivo enviado.");
        }
    }

    /** Recorre el SVG por bloques buscando construcciones activas; nunca lo carga entero en memoria. */
    private void verificarSvg(MultipartFile archivo) {
        try (Reader lector = new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8)) {
            char[] bloque = new char[8192];
            String cola = "";
            int leidos;
            while ((leidos = lector.read(bloque)) != -1) {
                String ventana = cola + new String(bloque, 0, leidos);
                if (SVG_PELIGROSO.matcher(ventana).find()) {
                    log.warn("SVG rechazado: contiene script, eventos, javascript: o entidades");
                    throw invalido("la imagen SVG contiene codigo (script, eventos o javascript:)",
                            "La imagen SVG contiene codigo ejecutable y no se acepta.");
                }
                cola = ventana.substring(Math.max(0, ventana.length() - SOLAPAMIENTO));
            }
        } catch (IOException ex) {
            throw invalido("no se pudo leer el archivo", "No se pudo leer el archivo enviado.");
        }
    }

    private static NegocioException invalido(String mensaje, String detalle) {
        return NegocioException.campoInvalido(CAMPO, mensaje, detalle);
    }
}
