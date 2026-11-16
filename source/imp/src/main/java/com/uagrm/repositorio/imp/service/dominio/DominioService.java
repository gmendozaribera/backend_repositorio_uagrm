package com.uagrm.repositorio.imp.service.dominio;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dao.repository.DominioRepository;
import com.uagrm.repositorio.dto.dominio.ActualizarDominioRequest;
import com.uagrm.repositorio.dto.dominio.CrearDominioRequest;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import com.uagrm.repositorio.dto.mapper.DominioMapper;
import com.uagrm.repositorio.imp.dominio.CifradorDominio;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.imp.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion de la tabla de dominio (parametros configurables, regla 11). Toda escritura borra la cache
 * Redis del grupo despues del commit: el cambio aplica al instante en todos los servicios.
 * Los parametros del sistema (declarados en {@link ConstantesDominio}) no se eliminan y su valor se valida.
 * Los secretos se guardan cifrados ({@link CifradorDominio}) y la respuesta nunca incluye su valor.
 */
@Service
@RequiredArgsConstructor
public class DominioService {

    private static final String CAMPO_VALOR = "domainValue";

    private final DominioRepository repository;
    private final DominioMapper mapper;
    private final ParametrosService parametros;
    private final CifradorDominio cifrador;

    @Transactional(readOnly = true)
    public List<DominioResponse> listar(String domainGroup) {
        List<Dominio> filas = domainGroup == null || domainGroup.isBlank()
                ? repository.findAllByOrderByDomainGroupAscDomainNameAsc()
                : repository.findByDomainGroupOrderByDomainNameAsc(codigo(domainGroup));
        return filas.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DominioResponse obtener(Integer id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public DominioResponse crear(Jwt admin, CrearDominioRequest request) {
        Dominio dominio = mapper.toEntity(request);
        dominio.setDomainGroup(codigo(request.domainGroup()));
        dominio.setDomainName(codigo(request.domainName()));
        if (repository.existsByDomainGroupAndDomainName(dominio.getDomainGroup(), dominio.getDomainName())) {
            throw NegocioException.conflicto("Ya existe el parametro " + dominio.getDomainGroup() + "."
                    + dominio.getDomainName() + ".");
        }
        normalizar(dominio);
        validarYCifrar(dominio);
        auditar(dominio, admin);
        Dominio guardado = repository.save(dominio);
        parametros.invalidar(guardado.getDomainGroup());
        return toResponse(guardado);
    }

    /**
     * Solo valor y descripcion: grupo y nombre son inmutables porque el codigo los referencia por nombre.
     * domainValue null/omitido conserva el valor actual tal cual (sin revalidar ni recifrar): asi se puede cambiar
     * la descripcion de un secreto sin conocer ni borrar su valor.
     */
    @Transactional
    public DominioResponse actualizar(Jwt admin, Integer id, ActualizarDominioRequest request) {
        Dominio dominio = buscar(id);
        mapper.actualizar(request, dominio);
        if (request.domainValue() == null) {
            dominio.setDescription(dominio.getDescription().trim());
        } else {
            normalizar(dominio);
            validarYCifrar(dominio);
        }
        auditar(dominio, admin);
        parametros.invalidar(dominio.getDomainGroup());
        return toResponse(dominio);
    }

    /** Borrado fisico; los parametros que usa el sistema no se pueden eliminar (409). */
    @Transactional
    public void eliminar(Integer id) {
        Dominio dominio = buscar(id);
        if (ConstantesDominio.esDelSistema(dominio.getDomainGroup(), dominio.getDomainName())) {
            throw NegocioException.conflicto("El parametro " + dominio.getDomainGroup() + "." + dominio.getDomainName()
                    + " lo usa el sistema y no se puede eliminar; puede cambiar su valor.");
        }
        repository.delete(dominio);
        parametros.invalidar(dominio.getDomainGroup());
    }

    // ---------- privados ----------

    private Dominio buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("No existe el parametro " + id + "."));
    }

    /** Un secreto nunca sale de aqui: domainValue = "" y configurado = tiene valor guardado (DominioMapper). */
    private DominioResponse toResponse(Dominio dominio) {
        Optional<Parametro> parametro = ConstantesDominio.buscar(dominio.getDomainGroup(), dominio.getDomainName());
        return mapper.toResponse(dominio, parametro.isPresent(), parametro.map(Parametro::secreto).orElse(false));
    }

    /**
     * Parametro del sistema: su valor debe cumplir el tipo y rango declarados (400 en domainValue); si es secreto
     * se valida el texto plano y se guarda cifrado (AES-GCM, vacio queda vacio). Parametro libre: no vacio.
     */
    private void validarYCifrar(Dominio dominio) {
        Optional<Parametro> parametro = ConstantesDominio.buscar(dominio.getDomainGroup(), dominio.getDomainName());
        Optional<String> error = parametro.isPresent()
                ? parametro.get().validar(dominio.getDomainValue())
                : dominio.getDomainValue().isEmpty() ? Optional.of("no puede estar vacio") : Optional.empty();
        error.ifPresent(mensaje -> {
            throw NegocioException.campoInvalido(CAMPO_VALOR, mensaje);
        });
        if (parametro.isPresent() && parametro.get().secreto()) {
            dominio.setDomainValue(cifrador.cifrar(dominio.getDomainValue()));
        }
    }

    /** Los secretos no se recortan: un espacio al inicio o al final puede ser parte de una contrasena. */
    private static void normalizar(Dominio dominio) {
        if (!ConstantesDominio.esSecreto(dominio.getDomainGroup(), dominio.getDomainName())) {
            dominio.setDomainValue(dominio.getDomainValue().trim());
        }
        dominio.setDescription(dominio.getDescription().trim());
    }

    /** lastUser = login del administrador autenticado (claim usuario del JWT); lastTime = ahora. */
    private static void auditar(Dominio dominio, Jwt admin) {
        dominio.setLastUser(admin.getClaimAsString(JwtService.CLAIM_USUARIO));
        dominio.setLastTime(Instant.now());
    }

    private static String codigo(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
