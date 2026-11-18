package com.uagrm.repositorio.imp.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.repository.FaseRepository;
import com.uagrm.repositorio.dao.repository.GrupoDocenteRepository;
import com.uagrm.repositorio.dao.repository.TrabajoRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;
import com.uagrm.repositorio.dto.mapper.TrabajoMapperImpl;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.EnlaceRequest;
import com.uagrm.repositorio.imp.almacenamiento.AlmacenamientoArchivos;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.LimiteSubidasService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.trabajo.AccesoTrabajos;
import com.uagrm.repositorio.mockup.CatalogoMock;
import com.uagrm.repositorio.mockup.TrabajoMock;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida, descarga y borrado de documentos: orden de las validaciones (limite antes de leer), SHA-256 al vuelo,
 * compensacion en MinIO si la BD falla, 503 si MinIO cae y permisos en cada descarga.
 */
class DocumentoServiceTest {

    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final FaseRepository faseRepository = mock(FaseRepository.class);
    private final FacultadRepository facultadRepository = mock(FacultadRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final TrabajoRepository trabajoRepository = mock(TrabajoRepository.class);
    private final GrupoDocenteRepository grupoDocenteRepository = mock(GrupoDocenteRepository.class);
    private final AlmacenamientoArchivos almacenamiento = mock(AlmacenamientoArchivos.class);
    private final LimiteSubidasService limiteSubidas = mock(LimiteSubidasService.class);
    private final ParametrosService parametros = mock(ParametrosService.class);
    private final ConfigFacultadService configFacultad = mock(ConfigFacultadService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AlmacenamientoArchivos> proveedor = mock(ObjectProvider.class);

    private DocumentoService service;
    private Trabajo trabajo;
    /** Lo que "llego" a MinIO en la ultima subida. */
    private final AtomicReference<byte[]> subido = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        AccesoTrabajos acceso = new AccesoTrabajos(usuarioRepository, trabajoRepository, grupoDocenteRepository);
        service = new DocumentoService(documentoRepository, faseRepository, facultadRepository, acceso,
                new ArchivosTrabajo(proveedor), new ValidadorArchivos(), new ValidadorEnlaces(), limiteSubidas,
                parametros, configFacultad, new TrabajoMapperImpl(), mock(PlatformTransactionManager.class));
        when(proveedor.getIfAvailable()).thenReturn(almacenamiento);
        for (Usuario u : new Usuario[]{TrabajoMock.autor(), TrabajoMock.otroEstudiante(), TrabajoMock.docente()}) {
            when(usuarioRepository.findConRolByIdUsuario(u.getIdUsuario())).thenReturn(Optional.of(u));
        }
        when(grupoDocenteRepository.findEsAdminCurso(40, TrabajoMock.ID_DOCENTE)).thenReturn(Optional.of(false));
        trabajo = TrabajoMock.trabajo(EstadoTrabajo.BORRADOR);
        when(trabajoRepository.findDetalleByIdTrabajo(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(trabajoRepository.findParaActualizar(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(trabajo));
        when(faseRepository.findById(50)).thenReturn(Optional.of(CatalogoMock.fase(CatalogoMock.facultad())));
        when(configFacultad.configDe(1)).thenReturn(new ConfigFacultadResponse(1, false, 10, 30, 50, false, false, 100, 1));
        when(parametros.entero(Parametro.WORK_VIDEO_MAX_MB)).thenReturn(100);
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            subido.set(inv.<InputStream>getArgument(1).readAllBytes());  // MinIO consume el stream
            return null;
        }).when(almacenamiento).subir(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void subirGuardaConKeyUuidYSha256CalculadoAlVuelo() throws Exception {
        DocumentoResponse respuesta = subir(pdf());

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).subir(clave.capture(), any(), eq((long) ValidadorArchivosTest.PDF.length),
                eq("application/pdf"));
        assertThat(clave.getValue()).matches("trabajos/7/[0-9a-f-]{36}\\.pdf");
        assertThat(subido.get()).isEqualTo(ValidadorArchivosTest.PDF);

        Documento guardado = documentoGuardado();
        assertThat(guardado.getRutaArchivo()).isEqualTo(clave.getValue());
        assertThat(guardado.getSha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(ValidadorArchivosTest.PDF)));
        assertThat(guardado.getNombreArchivo()).isEqualTo("informe final.pdf");
        assertThat(respuesta.tipo()).isEqualTo(TipoDocumento.ARCHIVO);
        assertThat(respuesta.nombreFase()).isEqualTo("Perfil");
    }

    @Test
    void siLaBdFallaDespuesDeSubirSeBorraElObjeto() {
        when(documentoRepository.save(any())).thenThrow(new IllegalStateException("BD caida"));
        assertThatThrownBy(() -> subir(pdf())).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).subir(clave.capture(), any(), anyLong(), anyString());
        verify(almacenamiento).borrar(clave.getValue());
    }

    @Test
    void siElTrabajoDejoDeSerEditableMientrasSubiaSeCompensaYDevuelve409() {
        Trabajo yaEnviado = TrabajoMock.trabajo(EstadoTrabajo.PENDIENTE);
        when(trabajoRepository.findParaActualizar(TrabajoMock.ID_TRABAJO)).thenReturn(Optional.of(yaEnviado));
        assertStatus(() -> subir(pdf()), HttpStatus.CONFLICT);
        verify(almacenamiento).borrar(anyString());
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void elLimiteDeSubidasSeAplicaAntesDeLeerElArchivo() {
        doThrow(new LimiteIntentosException(30, "Demasiadas subidas", "x")).when(limiteSubidas).registrar(TrabajoMock.ID_AUTOR);
        MultipartFile archivo = mock(MultipartFile.class);
        assertStatus(() -> service.subir(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, archivo, "Final", null,
                CategoriaDocumento.DOCUMENTO, 50), HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(archivo, almacenamiento);
    }

    @Test
    void minioCaidoDevuelve503SinRegistrarNada() {
        doThrow(NegocioException.noDisponible("MinIO caido")).when(almacenamiento)
                .subir(anyString(), any(), anyLong(), anyString());
        assertStatus(() -> subir(pdf()), HttpStatus.SERVICE_UNAVAILABLE);
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void sinAlmacenamientoConfiguradoDevuelve503() {
        when(proveedor.getIfAvailable()).thenReturn(null);
        assertStatus(() -> subir(pdf()), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void archivoFalsoSeRechazaSinSubirYCuentaParaElLimite() {
        assertStatus(() -> subir(ValidadorArchivosTest.archivo("tarea.pdf", ValidadorArchivosTest.zip("a.txt"))),
                HttpStatus.BAD_REQUEST);
        verify(limiteSubidas).registrar(TrabajoMock.ID_AUTOR);
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void otroEstudianteNoPuedeSubirNiDescargar() {
        Documento pdf = TrabajoMock.pdf(trabajo);
        when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(pdf));
        assertStatus(() -> service.subir(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), TrabajoMock.ID_TRABAJO, pdf(), "Final",
                null, CategoriaDocumento.DOCUMENTO, 50), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.descargar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), 70), HttpStatus.NOT_FOUND);
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void trabajoEvaluadoLoDescargaCualquierUsuarioAutenticado() {
        Trabajo evaluado = TrabajoMock.trabajo(EstadoTrabajo.EVALUADO);
        Documento pdf = TrabajoMock.pdf(evaluado);
        when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(pdf));
        InputStream contenido = new ByteArrayInputStream(ValidadorArchivosTest.PDF);
        when(almacenamiento.descargar(pdf.getRutaArchivo())).thenReturn(contenido);

        DescargaArchivo descarga = service.descargar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), 70);
        assertThat(descarga.contenido()).isSameAs(contenido);
        verify(grupoDocenteRepository, never()).findEsAdminCurso(any(), any());  // publicado: no hace falta ver la relacion
    }

    @Test
    void trabajoNoEvaluadoAjenoSigueSiendo404() {
        for (EstadoTrabajo estado : new EstadoTrabajo[]{EstadoTrabajo.APROBADO, EstadoTrabajo.PENDIENTE}) {
            Documento pdf = TrabajoMock.pdf(TrabajoMock.trabajo(estado));
            when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(pdf));
            assertStatus(() -> service.descargar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), 70), HttpStatus.NOT_FOUND);
        }
        verifyNoInteractions(almacenamiento);
    }

    @Test
    void autorYDocenteDelGrupoDescargan() {
        Documento pdf = TrabajoMock.pdf(trabajo);
        when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(pdf));
        InputStream contenido = new ByteArrayInputStream(ValidadorArchivosTest.PDF);
        when(almacenamiento.descargar(pdf.getRutaArchivo())).thenReturn(contenido);

        DescargaArchivo descarga = service.descargar(jwt(TrabajoMock.ID_AUTOR), 70);
        assertThat(descarga.contenido()).isSameAs(contenido);
        assertThat(descarga.contentType()).isEqualTo("application/pdf");
        assertThat(descarga.contentDisposition())
                .isEqualTo("attachment; filename=\"informe final.pdf\"; filename*=UTF-8''informe%20final.pdf");
        assertThat(service.descargar(jwt(TrabajoMock.ID_DOCENTE), 70).tamanoBytes()).isEqualTo(1024L);
    }

    @Test
    void descargarUnEnlaceDevuelve409() {
        Documento enlace = TrabajoMock.pdf(trabajo);
        enlace.setTipo(TipoDocumento.ENLACE);
        when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(enlace));
        assertStatus(() -> service.descargar(jwt(TrabajoMock.ID_AUTOR), 70), HttpStatus.CONFLICT);
    }

    @Test
    void nombreConTildesEnContentDisposition() {
        DescargaArchivo descarga = new DescargaArchivo("diseño \"final\".pdf", "application/pdf", 1, null);
        assertThat(descarga.contentDisposition()).isEqualTo(
                "attachment; filename=\"dise_o _final_.pdf\"; filename*=UTF-8''dise%C3%B1o%20%22final%22.pdf");
    }

    @Test
    void enlaceConHostParecidoSeRechaza() {
        EnlaceRequest request = new EnlaceRequest("Repo", null, "https://github.com.evil.com/x",
                CategoriaDocumento.CODIGO, 50);
        assertStatus(() -> service.agregarEnlace(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, request),
                HttpStatus.BAD_REQUEST);
        verify(documentoRepository, never()).save(any());

        DocumentoResponse ok = service.agregarEnlace(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO,
                new EnlaceRequest("Repo", null, "https://github.com/usuario/proyecto", CategoriaDocumento.CODIGO, 50));
        assertThat(ok.urlExterno()).isEqualTo("https://github.com/usuario/proyecto");
    }

    @Test
    void quitarDocumentoBorraElArchivoTrasElCommitYSoloSiEsEditable() {
        Documento pdf = TrabajoMock.pdf(trabajo);
        when(documentoRepository.findConTrabajoByIdDocumento(70)).thenReturn(Optional.of(pdf));
        service.eliminar(jwt(TrabajoMock.ID_AUTOR), 70);
        verify(documentoRepository).delete(pdf);
        verify(almacenamiento).borrar(pdf.getRutaArchivo());  // sin transaccion activa: al instante

        trabajo.setEstado(EstadoTrabajo.EN_REVISION);
        assertStatus(() -> service.eliminar(jwt(TrabajoMock.ID_AUTOR), 70), HttpStatus.CONFLICT);
        assertStatus(() -> service.eliminar(jwt(TrabajoMock.ID_OTRO_ESTUDIANTE), 70), HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private DocumentoResponse subir(MultipartFile archivo) {
        return service.subir(jwt(TrabajoMock.ID_AUTOR), TrabajoMock.ID_TRABAJO, archivo, "Documento final", null,
                CategoriaDocumento.DOCUMENTO, 50);
    }

    private static MultipartFile pdf() {
        return ValidadorArchivosTest.archivo("informe final.pdf", ValidadorArchivosTest.PDF);
    }

    private Documento documentoGuardado() {
        ArgumentCaptor<Documento> captor = ArgumentCaptor.forClass(Documento.class);
        verify(documentoRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Jwt jwt(int idUsuario) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(idUsuario)).build();
    }

    private static void assertStatus(Runnable accion, HttpStatus status) {
        assertThatThrownBy(accion::run).isInstanceOf(NegocioException.class).extracting("status").isEqualTo(status);
    }
}
