package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.trabajo.AccionesResponse;
import com.uagrm.repositorio.dto.trabajo.AutorResponse;
import com.uagrm.repositorio.dto.trabajo.CambioEstadoResponse;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.FormatoCategoriaResponse;
import com.uagrm.repositorio.dto.trabajo.ReglasDocumentosResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoResumenResponse;
import java.time.Instant;
import java.util.List;

/**
 * Builders de trabajos de prueba: un grupo SA de INF412 (FICCT, 2-2026), el estudiante autor (usuario 33,
 * persona 33, registro 219012345), un coautor (34), el docente del grupo (5) y un trabajo 7.
 */
public final class TrabajoMock {

    public static final int ID_TRABAJO = 7;
    public static final int ID_AUTOR = 33;
    public static final int REGISTRO_AUTOR = 219012345;
    public static final int ID_COAUTOR = 34;
    public static final int REGISTRO_COAUTOR = 219012346;
    public static final int ID_DOCENTE = 5;
    public static final int ID_OTRO_ESTUDIANTE = 40;
    public static final int ID_ADMIN = 1;
    private static final Instant FECHA = Instant.parse("2026-11-17T14:00:00Z");

    private TrabajoMock() {
    }

    // ---------- Entidades ----------

    public static Usuario autor() {
        return estudiante(ID_AUTOR, REGISTRO_AUTOR);
    }

    public static Usuario coautor() {
        return estudiante(ID_COAUTOR, REGISTRO_COAUTOR);
    }

    public static Usuario otroEstudiante() {
        return estudiante(ID_OTRO_ESTUDIANTE, 219099999);
    }

    public static Usuario estudiante(int id, int registro) {
        Usuario usuario = UsuarioMock.usuario(id, String.valueOf(registro), TipoUsuario.ESTUDIANTE);
        usuario.getPersona().setRegistro(registro);
        return usuario;
    }

    public static Usuario docente() {
        return CatalogoMock.docente();
    }

    public static Usuario admin() {
        return UsuarioMock.usuario(ID_ADMIN, "admin", TipoUsuario.ADMIN);
    }

    /** Trabajo 7 del grupo 40 en el estado dado, con el autor como creador y unico autor. */
    public static Trabajo trabajo(EstadoTrabajo estado) {
        Facultad facultad = CatalogoMock.facultad();
        Materia materia = CatalogoMock.materia(facultad);
        Semestre semestre = CatalogoMock.semestre(facultad);
        Grupo grupo = CatalogoMock.grupo(materia, semestre);
        Persona autor = autor().getPersona();

        Trabajo trabajo = new Trabajo();
        trabajo.setIdTrabajo(ID_TRABAJO);
        trabajo.setTitulo("Sistema de inventarios");
        trabajo.setResumen("Analisis, diseno e implementacion de un sistema de inventarios.");
        trabajo.setEstado(estado);
        trabajo.setCreador(autor);
        trabajo.setGrupo(grupo);
        trabajo.setMateria(materia);
        trabajo.setSemestre(semestre);
        trabajo.setTipoTrabajo(CatalogoMock.tipoTrabajo(facultad));
        trabajo.getAutores().add(autor);
        trabajo.setFechaCreacion(FECHA);
        trabajo.setFechaActualizacion(FECHA);
        return trabajo;
    }

    public static Documento pdf(Trabajo trabajo) {
        Documento documento = new Documento();
        documento.setIdDocumento(70);
        documento.setTrabajo(trabajo);
        documento.setFase(CatalogoMock.fase(CatalogoMock.facultad()));
        documento.setTitulo("Documento final");
        documento.setTipo(TipoDocumento.ARCHIVO);
        documento.setCategoria(CategoriaDocumento.DOCUMENTO);
        documento.setNombreArchivo("informe final.pdf");
        documento.setExtension("pdf");
        documento.setContentType("application/pdf");
        documento.setRutaArchivo("trabajos/7/11111111-2222-3333-4444-555555555555.pdf");
        documento.setTamanoBytes(1024L);
        documento.setSha256("a".repeat(64));
        documento.setFechaSubida(FECHA);
        return documento;
    }

    // ---------- Responses ----------

    public static DocumentoResponse documentoResponse() {
        return new DocumentoResponse(70, "Documento final", null, TipoDocumento.ARCHIVO, CategoriaDocumento.DOCUMENTO,
                50, "Perfil", "informe-final.pdf", "pdf", 1048576L, null, FECHA);
    }

    public static DocumentoResponse enlaceResponse() {
        return new DocumentoResponse(71, "Repositorio del proyecto", "Codigo fuente", TipoDocumento.ENLACE,
                CategoriaDocumento.CODIGO, 50, "Perfil", null, null, null, "https://github.com/usuario/proyecto", FECHA);
    }

    public static TrabajoResponse trabajoResponse(EstadoTrabajo estado, AccionesResponse acciones) {
        return new TrabajoResponse(ID_TRABAJO, "Sistema de inventarios",
                "Analisis, diseno e implementacion de un sistema de inventarios.", estado,
                estado == EstadoTrabajo.RECHAZADO ? "No corresponde al tema asignado." : null,
                estado == EstadoTrabajo.EVALUADO ? 85 : null, 100,
                40, "SA", 20, "INF412", "Ingenieria de Software I", 30, "2-2026", 1, 60, "Proyecto de grado",
                List.of(new AutorResponse(ID_AUTOR, "Juan", "Perez", REGISTRO_AUTOR, true),
                        new AutorResponse(ID_COAUTOR, "Ana", "Rojas", REGISTRO_COAUTOR, false)),
                List.of("inventarios", "springboot"),
                List.of(documentoResponse(), enlaceResponse()),
                List.of(new CambioEstadoResponse(null, EstadoTrabajo.BORRADOR, "Juan Perez", null, FECHA)),
                acciones, FECHA, FECHA, estado == EstadoTrabajo.BORRADOR ? null : FECHA);
    }

    public static TrabajoResumenResponse resumen(EstadoTrabajo estado) {
        return new TrabajoResumenResponse(ID_TRABAJO, "Sistema de inventarios", estado, 40, "SA", 20, "INF412",
                "Ingenieria de Software I", 30, "2-2026", "Proyecto de grado", List.of("Juan Perez", "Ana Rojas"),
                FECHA, estado == EstadoTrabajo.BORRADOR ? null : FECHA);
    }

    /** Tarjeta de un resultado del repositorio. */
    public static TrabajoRepositorioResumenResponse repositorioResumen() {
        return new TrabajoRepositorioResumenResponse(ID_TRABAJO, "Sistema de inventarios",
                "Analisis, diseno e implementacion de un sistema de inventarios.", List.of("inventarios", "springboot"),
                List.of("Juan Perez", "Ana Rojas"), "INF412", "Ingenieria de Software I", "2-2026", "Proyecto de grado",
                FECHA);
    }

    /** Ficha publica del repositorio (trabajo EVALUADO) vista por el autor. */
    public static TrabajoPublicoResponse publico() {
        return new TrabajoPublicoResponse(ID_TRABAJO, "Sistema de inventarios",
                "Analisis, diseno e implementacion de un sistema de inventarios.",
                40, "SA", 20, "INF412", "Ingenieria de Software I", 30, "2-2026", 1, 60, "Proyecto de grado",
                List.of(new AutorResponse(ID_AUTOR, "Juan", "Perez", REGISTRO_AUTOR, true),
                        new AutorResponse(ID_COAUTOR, "Ana", "Rojas", REGISTRO_COAUTOR, false)),
                List.of("inventarios", "springboot"),
                List.of(documentoResponse(), enlaceResponse()), FECHA);
    }

    public static ReglasDocumentosResponse reglas() {
        return new ReglasDocumentosResponse(1, List.of(
                new FormatoCategoriaResponse(CategoriaDocumento.DOCUMENTO, List.of("pdf", "docx", "odt"), false,
                        List.of(), 10),
                new FormatoCategoriaResponse(CategoriaDocumento.VIDEO, List.of("mp4", "webm"), true,
                        List.of("youtube.com", "www.youtube.com", "youtu.be", "vimeo.com", "drive.google.com"), 100)),
                10, 5);
    }
}
