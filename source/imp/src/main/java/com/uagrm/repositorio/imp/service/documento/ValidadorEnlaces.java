package com.uagrm.repositorio.imp.service.documento;

import com.uagrm.repositorio.dao.entity.CategoriaDocumento;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Valida un enlace externo. En terminos simples: solo se aceptan direcciones seguras (https) de sitios conocidos,
 * para que nadie use el repositorio para repartir enlaces a paginas falsas o maliciosas.
 * <ul>
 *   <li>Solo {@code https}: {@code http} viaja sin cifrar y {@code javascript:} o {@code data:} ejecutarian
 *       codigo al hacer clic.</li>
 *   <li>Host EXACTO de la lista de la categoria (VIDEO: YouTube, Vimeo, Google Drive; CODIGO: GitHub, GitLab).
 *       Nunca "contiene" ni "termina en": {@code github.com.evil.com} contiene "github.com" pero es otro sitio.</li>
 *   <li>Sin usuario en la URL: en {@code https://github.com@evil.com} el sitio real es evil.com.</li>
 *   <li>Sin puerto distinto de 443.</li>
 * </ul>
 */
@Component
public class ValidadorEnlaces {

    static final String CAMPO = "url";
    private static final int PUERTO_HTTPS = 443;

    /** @return la URL validada (sin espacios alrededor) */
    public String validar(String url, CategoriaDocumento categoria) {
        if (!ReglasDocumentos.permiteEnlace(categoria)) {
            throw NegocioException.campoInvalido("categoria", "solo VIDEO y CODIGO admiten enlaces");
        }
        String texto = url == null ? "" : url.strip();
        URI uri;
        try {
            uri = new URI(texto);
        } catch (URISyntaxException ex) {
            throw invalido("no es una URL valida");
        }
        if (uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw invalido("debe empezar con https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalido("no puede incluir usuario ni contrasena");
        }
        if (uri.getPort() != -1 && uri.getPort() != PUERTO_HTTPS) {
            throw invalido("no puede usar otro puerto");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);  // "github.com." es el mismo host escrito con punto final
        }
        List<String> permitidos = ReglasDocumentos.hosts(categoria);
        if (!permitidos.contains(host)) {
            throw invalido("el sitio no esta permitido para " + categoria + " (permitidos: "
                    + String.join(", ", permitidos) + ")");
        }
        return texto;
    }

    private static NegocioException invalido(String mensaje) {
        return NegocioException.campoInvalido(CAMPO, mensaje);
    }
}
