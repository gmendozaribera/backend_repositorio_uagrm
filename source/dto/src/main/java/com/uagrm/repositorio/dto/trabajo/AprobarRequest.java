package com.uagrm.repositorio.dto.trabajo;

import jakarta.validation.constraints.Size;

/** Con observaciones (texto no vacio) → APROBADO_CON_OBSERVACIONES; vacias o null → APROBADO. */
public record AprobarRequest(@Size(max = 3000) String observaciones) {
}
