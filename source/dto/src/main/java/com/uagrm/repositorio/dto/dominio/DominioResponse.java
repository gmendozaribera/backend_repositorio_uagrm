package com.uagrm.repositorio.dto.dominio;

import java.time.Instant;

/**
 * Parametro de configuracion.
 *
 * @param sistema true si el codigo usa este parametro (declarado en ConstantesDominio): no se puede eliminar
 *                y su valor se valida segun su tipo
 */
public record DominioResponse(
        Integer domainId,
        String domainGroup,
        String domainName,
        String domainValue,
        String description,
        String lastUser,
        Instant lastTime,
        boolean sistema) {
}
