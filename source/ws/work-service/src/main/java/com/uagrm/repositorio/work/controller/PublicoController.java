package com.uagrm.repositorio.work.controller;

import com.uagrm.repositorio.dto.common.ApiResponse;
import com.uagrm.repositorio.dto.common.PaginaResponse;
import com.uagrm.repositorio.dto.trabajo.EstadoAccesoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.FiltrosRepositorio;
import com.uagrm.repositorio.dto.trabajo.TrabajoPublicoResponse;
import com.uagrm.repositorio.dto.trabajo.TrabajoRepositorioResumenResponse;
import com.uagrm.repositorio.imp.service.trabajo.RepositorioService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repositorio sin iniciar sesion (solo GET; SecurityConfig ignora cualquier token en estas rutas). Que facultades
 * se muestran lo decide {@link RepositorioService} segun ConfigFacultad.accesoExterno. Solo delega.
 *
 * <p>TODO (rate limit anonimo): estas rutas no tienen usuario al que limitar y son las mas expuestas a scraping
 * (recorrer todas las paginas o todos los ids). Recomendado: limite por IP (IpCliente) con una ventana fija en
 * Redis, p. ej. {@code work:rate:public:{ip}} con 60 peticiones/minuto y TTL 60 s, que responda 429 con Retry-After
 * como LimiteSubidasService; si Redis cae, degradar a "sin limite" (no es auth). Pendiente de confirmar namespace
 * y TTL de la key (regla 2) y de agregar el 429 al contrato. Mientras tanto: tamano de pagina maximo 100 y la
 * busqueda usa el indice GIN.</p>
 */
@RestController
@RequestMapping("/api/v1/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final RepositorioService repositorioService;

    @GetMapping("/repositorio")
    public ApiResponse<PaginaResponse<TrabajoRepositorioResumenResponse>> buscar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 50) String hashtag,
            @RequestParam(required = false) @Positive Integer idGrupo,
            @RequestParam(required = false) @Positive Integer idFacultad,
            @RequestParam(required = false) @Positive Integer idCarrera,
            @RequestParam(required = false) @Positive Integer idMateria,
            @RequestParam(required = false) @Positive Integer idSemestre,
            @RequestParam(required = false) @Positive Integer idTipoTrabajo,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        FiltrosRepositorio filtros = new FiltrosRepositorio(q, hashtag, idGrupo, idFacultad, idCarrera, idMateria,
                idSemestre, idTipoTrabajo);
        return ApiResponse.ok("Trabajos obtenidos", repositorioService.buscarPublico(filtros, pagina, tamano));
    }

    @GetMapping("/repositorio/{id}")
    public ApiResponse<TrabajoPublicoResponse> obtener(@PathVariable @Positive Integer id) {
        return ApiResponse.ok("Trabajo obtenido", repositorioService.obtenerPublico(id));
    }

    @GetMapping("/estado")
    public ApiResponse<EstadoAccesoPublicoResponse> estado() {
        return ApiResponse.ok("Estado del acceso publico", repositorioService.estadoPublico());
    }
}
