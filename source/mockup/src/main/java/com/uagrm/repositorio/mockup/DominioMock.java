package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.Dominio;
import com.uagrm.repositorio.dto.dominio.DominioResponse;
import java.time.Instant;

/**
 * Builders de la tabla de dominio. Grupo y nombre los pasa cada test: para los parametros del sistema se usan
 * las constantes de imp ConstantesDominio (regla 11: nunca strings sueltos).
 */
public final class DominioMock {

    public static final Instant FECHA = Instant.parse("2026-11-16T14:00:00Z");

    private DominioMock() {
    }

    public static Dominio dominio(Integer id, String grupo, String nombre, String valor) {
        Dominio dominio = new Dominio();
        dominio.setDomainId(id);
        dominio.setDomainGroup(grupo);
        dominio.setDomainName(nombre);
        dominio.setDomainValue(valor);
        dominio.setDescription("Parametro " + nombre);
        dominio.setLastUser("sistema");
        dominio.setLastTime(FECHA);
        return dominio;
    }

    public static DominioResponse response(Integer id, String grupo, String nombre, String valor, boolean sistema) {
        return new DominioResponse(id, grupo, nombre, valor, "Parametro " + nombre, "admin", FECHA, sistema,
                false, true);
    }

    /** Respuesta de un parametro secreto: domainValue siempre vacio. */
    public static DominioResponse secreto(Integer id, String grupo, String nombre, boolean configurado) {
        return new DominioResponse(id, grupo, nombre, "", "Parametro " + nombre, "admin", FECHA, true,
                true, configurado);
    }
}
