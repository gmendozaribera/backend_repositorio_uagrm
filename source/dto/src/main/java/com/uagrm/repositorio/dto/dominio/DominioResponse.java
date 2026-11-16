package com.uagrm.repositorio.dto.dominio;

import java.time.Instant;

/**
 * Parametro de configuracion.
 *
 * @param domainValue vacio en los parametros secretos: su valor nunca sale de la API
 * @param sistema     true si el codigo usa este parametro (declarado en ConstantesDominio): no se puede eliminar
 *                    y su valor se valida segun su tipo
 * @param secreto     true si el valor se guarda cifrado y nunca se devuelve (p. ej. contrasenas)
 * @param configurado en secretos, true si ya tiene un valor guardado; en el resto siempre true
 */
public record DominioResponse(
        Integer domainId,
        String domainGroup,
        String domainName,
        String domainValue,
        String description,
        String lastUser,
        Instant lastTime,
        boolean sistema,
        boolean secreto,
        boolean configurado) {
}
