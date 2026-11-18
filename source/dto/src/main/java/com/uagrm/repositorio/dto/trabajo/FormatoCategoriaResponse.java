package com.uagrm.repositorio.dto.trabajo;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import java.util.List;

public record FormatoCategoriaResponse(CategoriaDocumento categoria, List<String> extensiones, boolean permiteEnlace,
                                       List<String> hostsEnlace, int tamanoMaxMB) {
}
