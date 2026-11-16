package com.uagrm.repositorio.imp.service.padron;

import static com.uagrm.repositorio.imp.service.padron.LectorPadron.APELLIDOS;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.CI;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.CORREO;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.NOMBRES;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.PASSWORD_DEFAULT;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.REGISTRO;
import static com.uagrm.repositorio.imp.service.padron.LectorPadron.SIGLA_CARRERA;

import com.uagrm.repositorio.dao.entity.CarnetIdentidad;
import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.EstudianteHabilitado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.repository.CarreraRepository;
import com.uagrm.repositorio.dao.repository.EstudianteHabilitadoRepository;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.specification.CatalogoSpecs;
import com.uagrm.repositorio.dao.specification.UsuarioSpecs;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.mapper.PadronMapper;
import com.uagrm.repositorio.dto.padron.ActualizarHabilitadoRequest;
import com.uagrm.repositorio.dto.padron.ErrorImportacion;
import com.uagrm.repositorio.dto.padron.EstudianteHabilitadoResponse;
import com.uagrm.repositorio.dto.padron.ResultadoImportacion;
import com.uagrm.repositorio.imp.exception.NegocioException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Padron estudiantil (CU-IMP): listado, habilitar/inhabilitar e importacion masiva desde CSV o Excel.
 * La importacion es un upsert por registro: cada fila se valida por separado y las filas con error se
 * informan sin interrumpir el resto. La contrasena por defecto se guarda como hash BCrypt.
 */
@Service
@RequiredArgsConstructor
public class PadronService {

    private static final Sort ORDEN = Sort.by("apellidos", "nombres", "id");
    private static final int LOTE = 500;
    private static final int PASSWORD_MIN = 4;
    /** BCrypt solo usa los primeros 72 bytes: una contrasena mas larga se rechaza en vez de truncarse en silencio. */
    private static final int PASSWORD_MAX = 72;
    private static final int TEXTO_MAX = 100;

    private final EstudianteHabilitadoRepository padronRepository;
    private final PersonaRepository personaRepository;
    private final CarreraRepository carreraRepository;
    private final PasswordEncoder passwordEncoder;
    private final LectorPadron lector;
    private final PadronMapper mapper;
    private final Validator validator;

    @Transactional(readOnly = true)
    public PaginaResponse<EstudianteHabilitadoResponse> listar(String q, Integer idCarrera, Boolean registrado,
                                                               Boolean habilitado, int pagina, int tamano) {
        Page<EstudianteHabilitado> page = padronRepository.findAll(
                UsuarioSpecs.textoPadron(q)
                        .and(CatalogoSpecs.<EstudianteHabilitado>idDe("carrera", "idCarrera", idCarrera))
                        .and(UsuarioSpecs.registrado(registrado))
                        .and(UsuarioSpecs.habilitado(habilitado)),
                PageRequest.of(pagina, tamano, ORDEN));
        Set<Integer> conCuenta = registrosConCuenta(page.getContent().stream().map(EstudianteHabilitado::getRegistro).toList());
        return PaginaResponse.de(page, e -> mapper.toResponse(e, conCuenta.contains(e.getRegistro())));
    }

    @Transactional
    public EstudianteHabilitadoResponse actualizarHabilitado(Integer id, ActualizarHabilitadoRequest request) {
        EstudianteHabilitado estudiante = padronRepository.findConCarreraById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el registro " + id + " del padron."));
        estudiante.setHabilitado(request.habilitado());
        return mapper.toResponse(estudiante, personaRepository.existsByRegistro(estudiante.getRegistro()));
    }

    @Transactional
    public ResultadoImportacion importar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Archivo invalido", "El archivo esta vacio.");
        }
        if (archivo.getSize() > LectorPadron.MAX_BYTES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Archivo invalido",
                    "El archivo supera el tamano maximo de 5 MB.");
        }
        try {
            return importar(lector.leer(archivo.getOriginalFilename(), archivo.getBytes()));
        } catch (IOException ex) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Archivo invalido", "No se pudo leer el archivo.");
        }
    }

    // ---------- importacion ----------

    /** Fila que paso la validacion individual, lista para el upsert. */
    private record FilaValida(int fila, Integer registro, String ci, String correo, String password,
                              String nombres, String apellidos, Carrera carrera) {
    }

    ResultadoImportacion importar(List<FilaPadron> filas) {
        List<ErrorImportacion> errores = new ArrayList<>();
        Set<Integer> filasConError = new HashSet<>();
        List<FilaValida> validas = validarFilas(filas, errores, filasConError);

        // Validaciones contra la BD, en bloque (una consulta por tipo, no una por fila)
        List<Integer> registros = validas.stream().map(FilaValida::registro).toList();
        List<String> cis = validas.stream().map(FilaValida::ci).toList();
        Map<Integer, EstudianteHabilitado> existentes = registros.isEmpty() ? Map.of()
                : padronRepository.findByRegistroIn(registros).stream()
                        .collect(Collectors.toMap(EstudianteHabilitado::getRegistro, Function.identity()));
        Map<String, Integer> registroDelCiEnPadron = cis.isEmpty() ? Map.of()
                : padronRepository.findByCiIn(cis).stream()
                        .collect(Collectors.toMap(EstudianteHabilitado::getCi, EstudianteHabilitado::getRegistro));
        Map<String, Persona> personaDelCi = cis.isEmpty() ? Map.of()
                : personaRepository.findByCiIn(cis).stream()
                        .collect(Collectors.toMap(Persona::getCi, Function.identity()));

        // Normalmente todo el padron comparte la misma contrasena por defecto: BCrypt (~100 ms) una vez por valor
        Map<String, String> hashes = new HashMap<>();
        Instant ahora = Instant.now();
        List<EstudianteHabilitado> aGuardar = new ArrayList<>();
        int insertados = 0;
        int actualizados = 0;
        for (FilaValida fila : validas) {
            Integer otroRegistro = registroDelCiEnPadron.get(fila.ci());
            if (otroRegistro != null && !otroRegistro.equals(fila.registro())) {
                rechazar(errores, filasConError, fila.fila(), CI,
                        "El CI " + fila.ci() + " ya pertenece al registro " + otroRegistro + " del padron.");
                continue;
            }
            Persona persona = personaDelCi.get(fila.ci());
            if (persona != null && !Objects.equals(persona.getRegistro(), fila.registro())) {
                rechazar(errores, filasConError, fila.fila(), CI,
                        "El CI " + fila.ci() + " ya pertenece a otra cuenta del sistema.");
                continue;
            }
            EstudianteHabilitado estudiante = existentes.get(fila.registro());
            if (estudiante == null) {
                estudiante = new EstudianteHabilitado();
                estudiante.setRegistro(fila.registro());
                insertados++;
            } else {
                actualizados++;
            }
            estudiante.setCi(fila.ci());
            estudiante.setCorreo(fila.correo());
            estudiante.setPasswordDefault(hashes.computeIfAbsent(fila.password(), passwordEncoder::encode));
            estudiante.setNombres(fila.nombres());
            estudiante.setApellidos(fila.apellidos());
            estudiante.setCarrera(fila.carrera());
            estudiante.setHabilitado(Boolean.TRUE);
            estudiante.setFechaImportacion(ahora);
            aGuardar.add(estudiante);
        }
        for (int i = 0; i < aGuardar.size(); i += LOTE) {
            padronRepository.saveAll(aGuardar.subList(i, Math.min(i + LOTE, aGuardar.size())));
            padronRepository.flush();
        }
        errores.sort(Comparator.comparingInt(ErrorImportacion::fila));
        return new ResultadoImportacion(filas.size(), insertados, actualizados, filasConError.size(), errores);
    }

    /** Validacion de cada fila por separado + repetidos dentro del mismo archivo (se rechaza la segunda aparicion). */
    private List<FilaValida> validarFilas(List<FilaPadron> filas, List<ErrorImportacion> errores,
                                          Set<Integer> filasConError) {
        Map<String, Carrera> carreras = carreraRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getSigla().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<Object, Integer> filaDelRegistro = new HashMap<>();
        Map<Object, Integer> filaDelCi = new HashMap<>();
        List<FilaValida> validas = new ArrayList<>();
        for (FilaPadron fila : filas) {
            List<ErrorImportacion> propios = new ArrayList<>();
            Integer registro = entero(fila, REGISTRO, propios);
            String ci = ci(fila, propios);
            String correo = correo(fila, propios);
            String password = password(fila, propios);
            String nombres = texto(fila, NOMBRES, propios);
            String apellidos = texto(fila, APELLIDOS, propios);
            Carrera carrera = carrera(fila, carreras, propios);
            repetido(fila, REGISTRO, registro, filaDelRegistro, propios);
            repetido(fila, CI, ci, filaDelCi, propios);
            if (propios.isEmpty()) {
                validas.add(new FilaValida(fila.fila(), registro, ci, correo, password, nombres, apellidos, carrera));
            } else {
                errores.addAll(propios);
                filasConError.add(fila.fila());
            }
        }
        return validas;
    }

    private static Integer entero(FilaPadron fila, String columna, List<ErrorImportacion> errores) {
        String valor = fila.valor(columna);
        if (valor.matches("\\d{1,10}")) {
            long numero = Long.parseLong(valor);
            if (numero > 0 && numero <= Integer.MAX_VALUE) {
                return (int) numero;
            }
        }
        errores.add(new ErrorImportacion(fila.fila(), columna,
                valor.isEmpty() ? "Es obligatorio." : "Debe ser un numero entero positivo: " + valor + "."));
        return null;
    }

    /**
     * CI como texto: sin espacios y en MAYUSCULAS, con el patron del contrato (1234567 o 1234567-1A).
     * Una celda numerica de Excel ya llega como "7654321" (LectorPadron quita el ".0").
     */
    private static String ci(FilaPadron fila, List<ErrorImportacion> errores) {
        String ci = CarnetIdentidad.normalizar(fila.valor(CI));
        if (ci.isEmpty()) {
            errores.add(new ErrorImportacion(fila.fila(), CI, "Es obligatorio."));
            return null;
        }
        if (!CarnetIdentidad.esValido(ci)) {
            errores.add(new ErrorImportacion(fila.fila(), CI,
                    "No es un CI valido: " + ci + " (4 a 12 digitos con complemento opcional, p. ej. 1234567-1A)."));
            return null;
        }
        return ci;
    }

    /** Usa las mismas reglas de Bean Validation de la entidad (@Email, @Size 150). Se guarda en minusculas. */
    private String correo(FilaPadron fila, List<ErrorImportacion> errores) {
        String correo = fila.valor(CORREO).toLowerCase(Locale.ROOT);
        if (correo.isEmpty()) {
            errores.add(new ErrorImportacion(fila.fila(), CORREO, "Es obligatorio."));
            return null;
        }
        if (!validator.validateValue(EstudianteHabilitado.class, "correo", correo).isEmpty()) {
            errores.add(new ErrorImportacion(fila.fila(), CORREO, "No es un correo valido: " + correo + "."));
            return null;
        }
        return correo;
    }

    private static String password(FilaPadron fila, List<ErrorImportacion> errores) {
        String password = fila.valor(PASSWORD_DEFAULT);
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX) {
            errores.add(new ErrorImportacion(fila.fila(), PASSWORD_DEFAULT,
                    "Debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres."));
            return null;
        }
        return password;
    }

    private static String texto(FilaPadron fila, String columna, List<ErrorImportacion> errores) {
        String valor = fila.valor(columna);
        if (valor.isEmpty()) {
            errores.add(new ErrorImportacion(fila.fila(), columna, "Es obligatorio."));
            return null;
        }
        if (valor.length() > TEXTO_MAX) {
            errores.add(new ErrorImportacion(fila.fila(), columna, "Maximo " + TEXTO_MAX + " caracteres."));
            return null;
        }
        return valor;
    }

    private static Carrera carrera(FilaPadron fila, Map<String, Carrera> carreras, List<ErrorImportacion> errores) {
        String sigla = fila.valor(SIGLA_CARRERA).toUpperCase(Locale.ROOT);
        if (sigla.isEmpty()) {
            errores.add(new ErrorImportacion(fila.fila(), SIGLA_CARRERA, "Es obligatoria."));
            return null;
        }
        Carrera carrera = carreras.get(sigla);
        if (carrera == null || !Objects.equals(carrera.getEstado(), Estado.ACTIVO)) {
            errores.add(new ErrorImportacion(fila.fila(), SIGLA_CARRERA,
                    "No existe una carrera activa con la sigla " + sigla + "."));
            return null;
        }
        return carrera;
    }

    private static void repetido(FilaPadron fila, String columna, Object valor, Map<Object, Integer> vistos,
                                 List<ErrorImportacion> errores) {
        if (valor == null) {
            return;
        }
        Integer primera = vistos.putIfAbsent(valor, fila.fila());
        if (primera != null) {
            errores.add(new ErrorImportacion(fila.fila(), columna,
                    "El " + columna + " " + valor + " esta repetido en el archivo (fila " + primera + ")."));
        }
    }

    private static void rechazar(List<ErrorImportacion> errores, Set<Integer> filasConError, int fila,
                                 String columna, String mensaje) {
        errores.add(new ErrorImportacion(fila, columna, mensaje));
        filasConError.add(fila);
    }

    private Set<Integer> registrosConCuenta(List<Integer> registros) {
        return registros.isEmpty() ? Set.of() : new HashSet<>(personaRepository.findRegistrosExistentes(registros));
    }
}
