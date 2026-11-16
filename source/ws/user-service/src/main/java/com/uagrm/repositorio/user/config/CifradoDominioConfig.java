package com.uagrm.repositorio.user.config;

import com.uagrm.repositorio.imp.dominio.CifradorDominio;
import org.springframework.context.annotation.Configuration;

/**
 * user-service es el unico que escribe y usa parametros secretos (CRUD /api/v1/dominios y prueba SMTP):
 * sin la llave de cifrado (DOMINIO_CIFRADO_KEY) no arranca, igual que sin JWT_SECRET. Asi un despliegue mal
 * configurado falla al inicio y no cuando un admin intenta guardar la contrasena SMTP.
 */
@Configuration
public class CifradoDominioConfig {

    CifradoDominioConfig(CifradorDominio cifrador) {
        if (!cifrador.disponible()) {
            throw new IllegalStateException(
                    "Falta app.dominio.cifrado-key (variable DOMINIO_CIFRADO_KEY): Base64 de 32 bytes, p. ej. openssl rand -base64 32");
        }
    }
}
