package com.uagrm.repositorio.imp.service.trabajo;

import com.uagrm.repositorio.dao.repository.ConfigFacultadRepository;
import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.service.catalogo.ConfigFacultadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que facultades permiten el acceso anonimo (ConfigFacultad.accesoExterno) al repositorio.
 *
 * <p>Una facultad sin fila propia en config_facultad usa el valor por defecto
 * FACULTAD_DEFAULT_GROUP.ACCESO_EXTERNO de la tabla dominio (sembrado en false): si un administrador lo cambia a
 * true, TODAS las facultades sin configuracion propia quedan abiertas; una facultad con fila propia manda su
 * propio valor. Es la misma regla de {@link ConfigFacultadService#configDe(Integer)}.</p>
 *
 * <p>Dos formas de leerlo, segun el caso:</p>
 * <ul>
 *   <li>Una facultad (ficha publica): {@code configDe(idFacultad)}, con su cache Redis
 *       {@code config:facultad:{id}}.</li>
 *   <li>La lista de facultades habilitadas (busqueda y /publico/estado): una sola consulta a config_facultad
 *       (LEFT JOIN facultad) con el default de dominio como parametro (ese default ya esta cacheado en
 *       {@code config:dominio:FACULTAD_DEFAULT_GROUP}). No se agrega una key Redis nueva (regla 2: requiere
 *       confirmacion); la consulta es indexada y pequena (una fila por facultad). El PUT de la configuracion
 *       se refleja de inmediato en la lista y, en la ficha, cuando se invalida su key (despues del commit).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccesoExternoService {

    private final ConfigFacultadRepository configFacultadRepository;
    private final ConfigFacultadService configFacultadService;
    private final ParametrosService parametros;

    /** Ids de las facultades con acceso externo habilitado, ordenados. Vacio = nadie entra sin sesion. */
    @Transactional(readOnly = true)
    public List<Integer> facultadesHabilitadas() {
        boolean porDefecto = parametros.booleano(Parametro.FACULTAD_ACCESO_EXTERNO);
        return configFacultadRepository.findIdsFacultadesConAccesoExterno(porDefecto);
    }

    /** true si la facultad permite ver sus trabajos evaluados sin iniciar sesion. */
    public boolean habilitada(Integer idFacultad) {
        return configFacultadService.configDe(idFacultad).accesoExterno();
    }
}
