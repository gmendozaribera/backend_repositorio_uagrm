package com.uagrm.repositorio.imp.service.documento;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.dao.entity.Documento;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Fase;
import com.uagrm.repositorio.dao.entity.TipoDocumento;
import com.uagrm.repositorio.dao.entity.Trabajo;
import com.uagrm.repositorio.dao.repository.DocumentoRepository;
import com.uagrm.repositorio.dao.repository.FacultadRepository;
import com.uagrm.repositorio.dao.repository.FaseRepository;
import com.uagrm.repositorio.dto.mapper.TrabajoMapper;
import com.uagrm.repositorio.dto.trabajo.DocumentoResponse;
import com.uagrm.repositorio.dto.trabajo.EnlaceRequest;
import com.uagrm.repositorio.dto.trabajo.FormatoCategoriaResponse;
import com.uagrm.repositorio.dto.trabajo.ReglasDocumentosResponse;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.redis.LimiteSubidasService;
import com.uagrm.repositorio.imp.security.UsuarioAutenticado;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import com.uagrm.repositorio.imp.service.documento.ValidadorArchivos.ArchivoValidado;
import com.uagrm.repositorio.imp.service.trabajo.AccesoTrabajos;
import com.uagrm.repositorio.imp.service.trabajo.ActorTrabajo;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos de un trabajo: archivos (MinIO) y enlaces externos.
 *
 * <p><b>Subida</b> ({@link #subir}), en este orden:</p>
 * <ol>
 *   <li>Limite de subidas por usuario (Redis) ANTES de mirar el archivo: 429 / 503.</li>
 *   <li>Permisos y fase en una transaccion corta de solo lectura (autor, trabajo editable, fase activa).</li>
 *   <li>Validacion del archivo: extension, tamano vigente y tipo real con Tika (400).</li>
 *   <li>Subida a MinIO en streaming FUERA de toda transaccion (un video de 100 MB no retiene una conexion de la
 *       BD), calculando el SHA-256 al vuelo. MinIO caido → 503.</li>
 *   <li>Registro en la BD en una transaccion nueva que vuelve a verificar permisos y estado (pudieron cambiar
 *       mientras subia). Si esta falla, se borra el objeto subido (compensacion): no quedan archivos huerfanos.</li>
 * </ol>
 *
 * <p><b>Descarga</b>: siempre pasa por aqui (proxy), nunca con un enlace directo a MinIO: los permisos se
 * verifican en CADA descarga, MinIO no se expone a internet y no circulan enlaces que se puedan reenviar.</p>
 */
@Slf4j
@Service
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final FaseRepository faseRepository;
    private final FacultadRepository facultadRepository;
    private final AccesoTrabajos acceso;
    private final ArchivosTrabajo archivos;
    private final ValidadorArchivos validadorArchivos;
    private final ValidadorEnlaces validadorEnlaces;
    private final LimiteSubidasService limiteSubidas;
    private final ParametrosService parametros;
    private final ConfigFacultadService configFacultad;
    private final TrabajoMapper mapper;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;

    public DocumentoService(DocumentoRepository documentoRepository, FaseRepository faseRepository,
                            FacultadRepository facultadRepository, AccesoTrabajos acceso, ArchivosTrabajo archivos,
                            ValidadorArchivos validadorArchivos, ValidadorEnlaces validadorEnlaces,
                            LimiteSubidasService limiteSubidas, ParametrosService parametros,
                            ConfigFacultadService configFacultad, TrabajoMapper mapper,
                            PlatformTransactionManager transactionManager) {
        this.documentoRepository = documentoRepository;
        this.faseRepository = faseRepository;
        this.facultadRepository = facultadRepository;
        this.acceso = acceso;
        this.archivos = archivos;
        this.validadorArchivos = validadorArchivos;
        this.validadorEnlaces = validadorEnlaces;
        this.limiteSubidas = limiteSubidas;
        this.parametros = parametros;
        this.configFacultad = configFacultad;
        this.mapper = mapper;
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
        this.escritura = new TransactionTemplate(transactionManager);
    }

    // ---------- Archivos ----------

    /** Sin @Transactional a proposito: la subida a MinIO ocurre entre dos transacciones cortas (ver la clase). */
    public DocumentoResponse subir(Jwt jwt, Integer idTrabajo, MultipartFile archivo, String titulo,
                                   String descripcion, CategoriaDocumento categoria, Integer idFase) {
        limiteSubidas.registrar(UsuarioAutenticado.id(jwt));

        Integer limiteMB = lectura.execute(status -> {
            ActorTrabajo actor = acceso.actor(jwt);
            Trabajo trabajo = acceso.paraLeer(idTrabajo, actor);
            acceso.exigirAutorEditable(trabajo, actor);
            Integer idFacultad = trabajo.getMateria().getFacultad().getIdFacultad();
            validarFase(idFase, idFacultad);
            return categoria == CategoriaDocumento.VIDEO
                    ? parametros.entero(Parametro.WORK_VIDEO_MAX_MB)
                    : configFacultad.configDe(idFacultad).tamanoMaxArchivoMB();
        });
        ArchivoValidado validado = validadorArchivos.validar(archivo, categoria, Objects.requireNonNull(limiteMB));

        String clave = ArchivosTrabajo.nuevaClave(idTrabajo, validado.extension());
        String sha256 = subirConHash(clave, archivo, validado.contentType());
        try {
            return escritura.execute(status -> {
                ActorTrabajo actor = acceso.actor(jwt);
                Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
                acceso.exigirAutorEditable(trabajo, actor);
                Documento documento = nuevoDocumento(trabajo, validarFase(idFase,
                        trabajo.getMateria().getFacultad().getIdFacultad()), titulo, descripcion, categoria,
                        TipoDocumento.ARCHIVO);
                documento.setNombreArchivo(validado.nombre());
                documento.setExtension(validado.extension());
                documento.setContentType(validado.contentType());
                documento.setRutaArchivo(clave);
                documento.setTamanoBytes(archivo.getSize());
                documento.setSha256(sha256);
                return mapper.toResponse(documentoRepository.save(documento));
            });
        } catch (RuntimeException ex) {
            // Compensacion: la BD no registro el archivo, asi que el objeto quedaria huerfano en MinIO
            log.warn("No se pudo registrar el documento {}; se borra el objeto subido: {}", clave, ex.getMessage());
            archivos.borrarSinRomper(clave);
            throw ex;
        }
    }

    /** Enlace externo (VIDEO o CODIGO, solo https y hosts permitidos). No cuenta para el limite de subidas. */
    @Transactional
    public DocumentoResponse agregarEnlace(Jwt jwt, Integer idTrabajo, EnlaceRequest request) {
        ActorTrabajo actor = acceso.actor(jwt);
        Trabajo trabajo = acceso.paraModificar(idTrabajo, actor);
        acceso.exigirAutorEditable(trabajo, actor);
        String url = validadorEnlaces.validar(request.url(), request.categoria());
        Fase fase = validarFase(request.idFase(), trabajo.getMateria().getFacultad().getIdFacultad());
        Documento documento = nuevoDocumento(trabajo, fase, request.titulo(), request.descripcion(),
                request.categoria(), TipoDocumento.ENLACE);
        documento.setUrlExterno(url);
        return mapper.toResponse(documentoRepository.save(documento));
    }

    /** Quita el documento (trabajo editable); su archivo se borra de MinIO despues del commit. */
    @Transactional
    public void eliminar(Jwt jwt, Integer idDocumento) {
        ActorTrabajo actor = acceso.actor(jwt);
        Documento documento = documentoRepository.findConTrabajoByIdDocumento(idDocumento)
                .orElseThrow(() -> noExiste(idDocumento));
        Trabajo trabajo;
        try {
            trabajo = acceso.paraModificar(documento.getTrabajo().getIdTrabajo(), actor);
        } catch (NegocioException ex) {
            throw noExiste(idDocumento);  // trabajo no visible: el documento tampoco "existe"
        }
        acceso.exigirAutorEditable(trabajo, actor);
        documentoRepository.delete(documento);
        trabajo.setFechaActualizacion(Instant.now());
        if (documento.getRutaArchivo() != null) {
            archivos.borrarTrasCommit(List.of(documento.getRutaArchivo()));
        }
    }

    /**
     * Proxy de descarga: verifica los permisos en cada descarga (trabajo EVALUADO: cualquier usuario autenticado;
     * si no, quien puede ver el trabajo). Enlace → 409.
     */
    @Transactional(readOnly = true)
    public DescargaArchivo descargar(Jwt jwt, Integer idDocumento) {
        ActorTrabajo actor = acceso.actor(jwt);
        Documento documento = documentoRepository.findConTrabajoByIdDocumento(idDocumento)
                .orElseThrow(() -> noExiste(idDocumento));
        if (!acceso.puedeDescargar(documento.getTrabajo(), actor)) {
            throw noExiste(idDocumento);
        }
        if (documento.getTipo() == TipoDocumento.ENLACE) {
            throw NegocioException.conflicto("El documento " + idDocumento
                    + " es un enlace externo: se abre directamente su urlExterno.");
        }
        InputStream contenido = archivos.almacenamiento().descargar(documento.getRutaArchivo());
        return new DescargaArchivo(documento.getNombreArchivo(), documento.getContentType(),
                documento.getTamanoBytes(), contenido);
    }

    /** Reglas vigentes para una facultad (404 si no existe). */
    @Transactional(readOnly = true)
    public ReglasDocumentosResponse reglas(Integer idFacultad) {
        if (!facultadRepository.existsById(idFacultad)) {
            throw NegocioException.noEncontrado("No existe la facultad " + idFacultad + ".");
        }
        int tamanoFacultad = configFacultad.configDe(idFacultad).tamanoMaxArchivoMB();
        int tamanoVideo = parametros.entero(Parametro.WORK_VIDEO_MAX_MB);
        List<FormatoCategoriaResponse> formatos = Arrays.stream(CategoriaDocumento.values())
                .map(c -> new FormatoCategoriaResponse(c, ReglasDocumentos.EXTENSIONES.get(c),
                        ReglasDocumentos.permiteEnlace(c), ReglasDocumentos.hosts(c),
                        c == CategoriaDocumento.VIDEO ? tamanoVideo : tamanoFacultad))
                .toList();
        return new ReglasDocumentosResponse(idFacultad, formatos, parametros.entero(Parametro.WORK_UPLOADS_POR_MINUTO),
                parametros.entero(Parametro.WORK_MAX_AUTORES));
    }

    // ---------- privados ----------

    /** Fase existente (404), de la facultad del trabajo (400) y activa (409). */
    private Fase validarFase(Integer idFase, Integer idFacultad) {
        Fase fase = faseRepository.findById(idFase)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe la fase " + idFase + "."));
        if (!Objects.equals(fase.getFacultad().getIdFacultad(), idFacultad)) {
            throw NegocioException.campoInvalido("idFase", "no pertenece a la facultad del trabajo");
        }
        if (!Objects.equals(fase.getEstado(), Estado.ACTIVO)) {
            throw NegocioException.conflicto("La fase " + fase.getNombre() + " esta inactiva.");
        }
        return fase;
    }

    private static Documento nuevoDocumento(Trabajo trabajo, Fase fase, String titulo, String descripcion,
                                            CategoriaDocumento categoria, TipoDocumento tipo) {
        Instant ahora = Instant.now();
        Documento documento = new Documento();
        documento.setTrabajo(trabajo);
        documento.setFase(fase);
        documento.setTitulo(titulo.strip());
        documento.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.strip());
        documento.setCategoria(categoria);
        documento.setTipo(tipo);
        documento.setFechaSubida(ahora);
        trabajo.setFechaActualizacion(ahora);
        return documento;
    }

    /** Sube en streaming y devuelve el SHA-256 (hex) calculado mientras los bytes pasan hacia MinIO. */
    private String subirConHash(String clave, MultipartFile archivo, String contentType) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("La JVM no soporta SHA-256", ex);
        }
        try (InputStream contenido = new DigestInputStream(archivo.getInputStream(), sha256)) {
            archivos.almacenamiento().subir(clave, contenido, archivo.getSize(), contentType);
        } catch (IOException ex) {
            throw NegocioException.campoInvalido(ValidadorArchivos.CAMPO, "no se pudo leer el archivo",
                    "No se pudo leer el archivo enviado.");
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static NegocioException noExiste(Integer idDocumento) {
        return NegocioException.noEncontrado("No existe el documento " + idDocumento + ".");
    }
}
