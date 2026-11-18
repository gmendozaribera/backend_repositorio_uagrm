package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.ConfigFacultad;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadRequest;
import com.uagrm.repositorio.dto.catalogo.ConfigFacultadResponse;

/** Builders de la configuracion por facultad (valores por defecto iguales a la migracion V6). */
public final class ConfigFacultadMock {

    public static final int HORAS_POR_DEFECTO = 1;

    private ConfigFacultadMock() {
    }

    /** Configuracion sin personalizar: defaults de FACULTAD_DEFAULT_GROUP. */
    public static ConfigFacultadResponse porDefecto(Integer idFacultad) {
        return new ConfigFacultadResponse(idFacultad, false, 10, 30, 50, false, false, 100, HORAS_POR_DEFECTO);
    }

    public static ConfigFacultadResponse personalizada(Integer idFacultad, int horasJwt) {
        return new ConfigFacultadResponse(idFacultad, true, 20, 25, 60, true, false, 70, horasJwt);
    }

    /** Personalizada con la escala de calificacion y el acceso externo dados (resto igual a los defaults). */
    public static ConfigFacultadResponse con(Integer idFacultad, int escalaCalifMax, boolean accesoExterno) {
        return new ConfigFacultadResponse(idFacultad, true, 10, 30, 50, false, accesoExterno, escalaCalifMax,
                HORAS_POR_DEFECTO);
    }

    public static ConfigFacultadRequest request(int medio, int alto) {
        return new ConfigFacultadRequest(20, medio, alto, true, false, 70, 8);
    }

    public static ConfigFacultad entidad(Facultad facultad, int horasJwt) {
        ConfigFacultad config = new ConfigFacultad();
        config.setId(1);
        config.setFacultad(facultad);
        config.setTamanoMaxArchivoMB(20);
        config.setUmbralPlagioMedio(25);
        config.setUmbralPlagioAlto(60);
        config.setComparacionInternet(true);
        config.setAccesoExterno(false);
        config.setEscalaCalifMax(70);
        config.setJwtExpiracionHoras(horasJwt);
        return config;
    }
}
