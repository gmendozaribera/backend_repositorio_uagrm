package com.uagrm.repositorio.dto.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Pagina de un listado: { contenido, pagina (desde 0), tamano, totalElementos, totalPaginas }.
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaResponse<>(page.getContent().stream().map(mapeo).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
