package com.uagrm.repositorio.imp.service.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.imp.exception.NegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Enlaces externos: solo https y host exacto de la lista de la categoria. */
class ValidadorEnlacesTest {

    private final ValidadorEnlaces validador = new ValidadorEnlaces();

    @Test
    void hostsPermitidos() {
        assertThat(validador.validar(" https://github.com/usuario/proyecto ", CategoriaDocumento.CODIGO))
                .isEqualTo("https://github.com/usuario/proyecto");
        validador.validar("https://GitLab.com/grupo/repo", CategoriaDocumento.CODIGO);
        validador.validar("https://www.youtube.com/watch?v=abc", CategoriaDocumento.VIDEO);
        validador.validar("https://youtu.be/abc", CategoriaDocumento.VIDEO);
        validador.validar("https://vimeo.com/123", CategoriaDocumento.VIDEO);
        validador.validar("https://drive.google.com/file/d/xyz/view", CategoriaDocumento.VIDEO);
        validador.validar("https://github.com.:443/usuario", CategoriaDocumento.CODIGO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com.evil.com/usuario/proyecto",   // contiene "github.com" pero es otro sitio
            "https://evilgithub.com/usuario",                 // termina en "github.com"
            "https://github.com@evil.com/usuario",            // el host real es evil.com
            "https://usuario:clave@github.com/repo",          // credenciales en la URL
            "http://github.com/usuario",                      // sin cifrar
            "javascript:alert(1)",                            // ejecutaria codigo
            "https://github.com:8443/usuario",                // otro puerto
            "https://gist.github.com/usuario",                // subdominio no listado
            "github.com/usuario",                             // sin esquema
            "https://github.com\\@evil.com",                  // URL mal formada
            "https://youtube.com/watch?v=1"                   // host de VIDEO en CODIGO
    })
    void enlacesRechazadosParaCodigo(String url) {
        assertThatThrownBy(() -> validador.validar(url, CategoriaDocumento.CODIGO))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores()).containsKey("url"));
    }

    @Test
    void categoriaSinEnlacesSeRechaza() {
        assertThatThrownBy(() -> validador.validar("https://github.com/a", CategoriaDocumento.DOCUMENTO))
                .isInstanceOf(NegocioException.class)
                .satisfies(ex -> assertThat(((NegocioException) ex).getErrores()).containsKey("categoria"));
    }
}
