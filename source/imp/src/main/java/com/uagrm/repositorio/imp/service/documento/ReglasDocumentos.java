package com.uagrm.repositorio.imp.service.documento;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formatos y hosts permitidos por categoria (contrato work-api.yaml, seccion "Archivos"). Fijos en el codigo: son
 * reglas de seguridad, no parametros de negocio (los tamanos y el limite de subidas si son configurables).
 */
public final class ReglasDocumentos {

    /** Extensiones permitidas por categoria (minusculas, en el orden en que se muestran). */
    public static final Map<CategoriaDocumento, List<String>> EXTENSIONES = Map.of(
            CategoriaDocumento.DOCUMENTO, List.of("pdf", "docx", "odt"),
            CategoriaDocumento.PRESENTACION, List.of("pptx", "pdf"),
            CategoriaDocumento.CODIGO, List.of("zip", "rar", "7z"),
            CategoriaDocumento.VIDEO, List.of("mp4", "webm"),
            CategoriaDocumento.IMAGEN, List.of("png", "jpg", "jpeg", "svg"));

    /** Hosts permitidos para enlaces externos (comparacion EXACTA del host, nunca "contiene"). */
    public static final Map<CategoriaDocumento, List<String>> HOSTS_ENLACE = Map.of(
            CategoriaDocumento.VIDEO, List.of("youtube.com", "www.youtube.com", "youtu.be", "vimeo.com",
                    "drive.google.com"),
            CategoriaDocumento.CODIGO, List.of("github.com", "gitlab.com"));

    /** Extension que exige "enviar a revision" (al menos un archivo asi). */
    public static final String EXTENSION_PDF = "pdf";

    /** Content-Type con el que se guarda y se descarga cada extension (el canonico, no el que manda el navegador). */
    static final Map<String, String> CONTENT_TYPE = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml"));

    private static final String ZIP = "application/zip";
    private static final String OOXML = "application/x-tika-ooxml";

    /**
     * Tipos que Tika (solo tika-core, deteccion por magic bytes) puede reportar para un archivo legitimo de cada
     * extension. Se compara el tipo base (sin parametros).
     *
     * <p>docx, pptx y odt son archivos ZIP por dentro. tika-core sin los parsers no siempre distingue el tipo
     * exacto: si el primer elemento del ZIP es [Content_Types].xml reporta application/x-tika-ooxml, si es el
     * archivo "mimetype" de ODF reporta el tipo ODF, y en otro caso application/zip. Por eso se aceptan el tipo
     * especifico, el generico OOXML y application/zip. Lo que SI se garantiza: un ejecutable, un script, un PDF o
     * una imagen renombrados a .docx se rechazan, porque no empiezan con la firma de un ZIP.</p>
     */
    static final Map<String, Set<String>> TIPOS_REALES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("docx", Set.of(CONTENT_TYPE.get("docx"), OOXML, ZIP)),
            Map.entry("pptx", Set.of(CONTENT_TYPE.get("pptx"), OOXML, ZIP)),
            Map.entry("odt", Set.of(CONTENT_TYPE.get("odt"), ZIP)),
            Map.entry("zip", Set.of(ZIP)),
            Map.entry("rar", Set.of("application/x-rar-compressed", "application/vnd.rar")),
            Map.entry("7z", Set.of("application/x-7z-compressed")),
            Map.entry("mp4", Set.of("video/mp4", "video/quicktime", "application/mp4", "video/x-m4v", "audio/mp4")),
            Map.entry("webm", Set.of("video/webm", "audio/webm", "video/x-matroska", "application/x-matroska")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("svg", Set.of("image/svg+xml")));

    private ReglasDocumentos() {
    }

    public static boolean permiteEnlace(CategoriaDocumento categoria) {
        return HOSTS_ENLACE.containsKey(categoria);
    }

    public static List<String> hosts(CategoriaDocumento categoria) {
        return HOSTS_ENLACE.getOrDefault(categoria, List.of());
    }
}
