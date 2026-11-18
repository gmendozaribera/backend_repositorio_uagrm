package com.uagrm.repositorio.imp.service.trabajo;

import static org.assertj.core.api.Assertions.assertThat;

import com.uagrm.repositorio.dto.mapper.TrabajoMapper;
import com.uagrm.repositorio.dto.mapper.TrabajoMapperImpl;
import org.junit.jupiter.api.Test;

/** Extracto de las tarjetas del repositorio: ~200 caracteres, corte en limite de palabra, "..." si se corto. */
class ExtractoRepositorioTest {

    private final TrabajoMapper mapper = new TrabajoMapperImpl();

    @Test
    void resumenCortoQuedaIgualSinPuntosSuspensivos() {
        assertThat(mapper.extracto("  Análisis y diseño.  ")).isEqualTo("Análisis y diseño.");
        String exacto = "a".repeat(TrabajoMapper.LARGO_EXTRACTO);
        assertThat(mapper.extracto(exacto)).isEqualTo(exacto);
        assertThat(mapper.extracto(null)).isNull();
    }

    @Test
    void resumenLargoSeCortaEnLimiteDePalabraConTildes() {
        // 39 x "Diseño " (7 caracteres) = 273; el limite cae dentro de una palabra
        String resumen = "Diseño ".repeat(39) + "información";
        String extracto = mapper.extracto(resumen);

        assertThat(extracto).endsWith("Diseño...");             // palabra completa, con su tilde
        assertThat(extracto.length() - 3).isLessThanOrEqualTo(TrabajoMapper.LARGO_EXTRACTO);
        assertThat(resumen).startsWith(extracto.substring(0, extracto.length() - 3));
        assertThat(extracto).doesNotContain("Diseñ...").doesNotContain("Dise...");
    }

    @Test
    void sinPuntuacionColgandoYSaltosDeLineaColapsados() {
        String resumen = "x".repeat(190) + ", final\ncon\n\nmas texto que sobra del limite de doscientos";
        // tras colapsar: el limite (200) cae dentro de "con"; se corta en el espacio anterior
        assertThat(mapper.extracto(resumen)).isEqualTo("x".repeat(190) + ", final...").doesNotContain("\n");
        assertThat(mapper.extracto("y".repeat(195) + ". " + "z".repeat(50))).isEqualTo("y".repeat(195) + "...");
    }

    @Test
    void palabraEnormeSeCortaEnElLimite() {
        String url = "https://" + "a".repeat(300);
        assertThat(mapper.extracto("Ver " + url)).hasSize(TrabajoMapper.LARGO_EXTRACTO + 3).endsWith("a...");
    }
}
