package com.uagrm.repositorio.imp.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.service.documento.ValidadorArchivos.ArchivoValidado;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Validacion de archivos con contenido real (magic bytes): extension falsa, SVG con codigo, tamano y nombre. */
class ValidadorArchivosTest {

    static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    /** Cabecera de un ejecutable de Windows (PE): empieza con "MZ". */
    static final byte[] EXE = exe();
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R',
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1f, 0x15, (byte) 0xc4, (byte) 0x89};
    static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 'i', 's', 'o', 'm',
            'i', 's', 'o', '2', 0, 0, 0, 8, 'f', 'r', 'e', 'e'};
    static final String SVG_LIMPIO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10"/></svg>
            """;

    private final ValidadorArchivos validador = new ValidadorArchivos();

    @Test
    void pdfRealSeAceptaConSuTipoCanonico() {
        ArchivoValidado validado = validar("informe final.pdf", PDF, CategoriaDocumento.DOCUMENTO);
        assertThat(validado.extension()).isEqualTo("pdf");
        assertThat(validado.contentType()).isEqualTo("application/pdf");
        assertThat(validado.nombre()).isEqualTo("informe final.pdf");
    }

    @Test
    void ejecutableRenombradoAPdfSeRechaza() {
        assertRechazado("tarea.pdf", EXE, CategoriaDocumento.DOCUMENTO, "no corresponde");
    }

    @Test
    void zipRenombradoAPdfSeRechaza() {
        assertRechazado("tarea.pdf", zip("codigo/Main.java"), CategoriaDocumento.DOCUMENTO, "no corresponde");
    }

    @Test
    void docxSeAceptaComoOoxmlOZipYUnPdfRenombradoADocxNo() {
        assertThat(validar("informe.docx", zip("[Content_Types].xml"), CategoriaDocumento.DOCUMENTO).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        // tika-core no distingue un ZIP generico de un docx: se acepta (documentado en ReglasDocumentos)
        validar("informe.docx", zip("word/document.xml"), CategoriaDocumento.DOCUMENTO);
        assertRechazado("informe.docx", PDF, CategoriaDocumento.DOCUMENTO, "no corresponde");
    }

    @Test
    void zipDeCodigoPngYMp4Reales() {
        validar("proyecto.zip", zip("src/Main.java"), CategoriaDocumento.CODIGO);
        validar("diagrama.PNG", PNG, CategoriaDocumento.IMAGEN);
        validar("demo.mp4", MP4, CategoriaDocumento.VIDEO);
        assertRechazado("demo.mp4", PNG, CategoriaDocumento.VIDEO, "no corresponde");
    }

    @Test
    void extensionFueraDeLaCategoriaOSinExtensionSeRechaza() {
        assertRechazado("video.mp4", MP4, CategoriaDocumento.DOCUMENTO, "formato no permitido");
        assertRechazado("programa.exe", EXE, CategoriaDocumento.CODIGO, "formato no permitido");
        assertRechazado("sin-extension", PDF, CategoriaDocumento.DOCUMENTO, "formato no permitido");
    }

    @Test
    void tamanoMayorAlLimiteSeRechazaConElLimiteVigente() {
        byte[] grande = new byte[1024 * 1024 + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);
        assertThatThrownBy(() -> validador.validar(archivo("grande.pdf", grande), CategoriaDocumento.DOCUMENTO, 1))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("1 MB de la facultad")
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores())
                        .containsEntry("archivo", "supera el tamano maximo de 1 MB"));
        assertThatThrownBy(() -> validador.validar(archivo("demo.mp4", grande), CategoriaDocumento.VIDEO, 1))
                .hasMessageContaining("para videos");
        assertRechazado("vacio.pdf", new byte[0], CategoriaDocumento.DOCUMENTO, "vacio");
    }

    @Test
    void svgLimpioSeAcepta() {
        assertThat(validar("logo.svg", SVG_LIMPIO.getBytes(StandardCharsets.UTF_8), CategoriaDocumento.IMAGEN)
                .contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void svgConCodigoSeRechaza() {
        assertSvgRechazado(SVG_LIMPIO.replace("<rect", "<script>alert(document.cookie)</script><rect"));
        assertSvgRechazado(SVG_LIMPIO.replace("<rect", "<rect onload=\"fetch('//evil.com')\""));
        assertSvgRechazado(SVG_LIMPIO.replace("<rect", "<a href=\"JavaScript:alert(1)\"><rect"));
        assertSvgRechazado(SVG_LIMPIO.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x \"y\">]>"));
        // El script al final de un archivo grande (lectura por bloques) tambien se detecta
        String largo = SVG_LIMPIO.replace("<rect", "<!--" + "x".repeat(20_000) + "--><rect")
                .replace("</svg>", "<script>alert(1)</script></svg>");
        assertSvgRechazado(largo);
    }

    @Test
    void nombreSaneado() {
        assertThat(ValidadorArchivos.sanearNombre("C:\\Users\\juan\\informe.pdf")).isEqualTo("informe.pdf");
        assertThat(ValidadorArchivos.sanearNombre("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(ValidadorArchivos.sanearNombre("..pdf")).isEqualTo("pdf");
        assertThat(ValidadorArchivos.sanearNombre("in\u0000for\"me.pdf")).isEqualTo("informe.pdf");
        assertThat(ValidadorArchivos.sanearNombre(null)).isEqualTo("archivo");
        String largo = ValidadorArchivos.sanearNombre("a".repeat(300) + ".pdf");
        assertThat(largo).hasSize(200).endsWith(".pdf");
    }

    // ---------- helpers ----------

    private ArchivoValidado validar(String nombre, byte[] contenido, CategoriaDocumento categoria) {
        return validador.validar(archivo(nombre, contenido), categoria, 10);
    }

    private void assertRechazado(String nombre, byte[] contenido, CategoriaDocumento categoria, String mensaje) {
        assertThatThrownBy(() -> validar(nombre, contenido, categoria))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores().get("archivo")).contains(mensaje));
    }

    private void assertSvgRechazado(String svg) {
        assertRechazado("logo.svg", svg.getBytes(StandardCharsets.UTF_8), CategoriaDocumento.IMAGEN, "SVG");
    }

    static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
    }

    static byte[] zip(String primeraEntrada) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(primeraEntrada));
            zip.write("contenido".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] exe() {
        byte[] exe = new byte[256];
        exe[0] = 'M';
        exe[1] = 'Z';
        exe[0x3c] = (byte) 0x80;  // offset de la cabecera PE
        exe[0x80] = 'P';
        exe[0x81] = 'E';
        return exe;
    }
}
