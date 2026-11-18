package com.uagrm.repositorio.imp.service.documento;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Archivo listo para devolver en la descarga (proxy en streaming desde MinIO).
 *
 * @param contenido stream abierto contra MinIO: quien lo escribe en la respuesta debe cerrarlo
 */
public record DescargaArchivo(String nombreArchivo, String contentType, long tamanoBytes, InputStream contenido) {

    /** Caracteres que RFC 5987 permite sin codificar en filename*. */
    private static final String ATTR_CHAR = "!#$&+-.^_`|~";

    /**
     * {@code attachment; filename="<ascii>"; filename*=UTF-8''<codificado>} (RFC 6266 + RFC 5987): "attachment"
     * obliga al navegador a guardar el archivo en lugar de abrirlo (un SVG o HTML nunca se ejecuta en nuestro
     * dominio); filename* conserva tildes y enies; filename es la version ASCII para clientes viejos.
     */
    public String contentDisposition() {
        return "attachment; filename=\"" + ascii(nombreArchivo) + "\"; filename*=UTF-8''" + rfc5987(nombreArchivo);
    }

    private static String ascii(String nombre) {
        StringBuilder sb = new StringBuilder(nombre.length());
        for (char c : nombre.toCharArray()) {
            sb.append(c >= 0x20 && c < 0x7f && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    private static String rfc5987(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (byte b : nombre.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || ATTR_CHAR.indexOf(c) >= 0) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return sb.toString();
    }
}
