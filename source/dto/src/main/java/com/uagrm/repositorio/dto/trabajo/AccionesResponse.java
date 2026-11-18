package com.uagrm.repositorio.dto.trabajo;

/** Lo que el usuario autenticado puede hacer ahora con el trabajo (el servidor lo vuelve a validar en cada accion). */
public record AccionesResponse(boolean editar, boolean enviar, boolean eliminar, boolean revisar, boolean aprobar,
                               boolean rechazar) {

    public static final AccionesResponse NINGUNA = new AccionesResponse(false, false, false, false, false, false);
}
