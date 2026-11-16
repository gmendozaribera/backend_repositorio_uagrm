package com.uagrm.repositorio.imp.redis;

/**
 * Catalogos cacheados en Redis y su segmento en la key catalog:cache:{entidad}:...
 * Grupos y docentes de grupo NO se cachean: cambian seguido durante el semestre.
 */
public enum EntidadCatalogo {

    FACULTADES("facultades"),
    CARRERAS("carreras"),
    MATERIAS("materias"),
    SEMESTRES("semestres"),
    FASES("fases"),
    TIPOS_TRABAJO("tipos-trabajo");

    private final String segmento;

    EntidadCatalogo(String segmento) {
        this.segmento = segmento;
    }

    public String segmento() {
        return segmento;
    }
}
