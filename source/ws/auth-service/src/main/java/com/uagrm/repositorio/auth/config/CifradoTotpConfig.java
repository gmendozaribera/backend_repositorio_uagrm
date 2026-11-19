package com.uagrm.repositorio.auth.config;

import com.uagrm.repositorio.imp.security.CifradorTotp;
import com.uagrm.repositorio.imp.service.MigracionTotpService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service es el unico que lee y escribe el secreto TOTP (2FA): sin la llave de cifrado (TOTP_CIFRADO_KEY) no
 * arranca, igual que sin JWT_SECRET. Asi un despliegue mal configurado falla al inicio y no cuando un usuario
 * intenta activar o usar la verificacion en dos pasos.
 *
 * <p>Ademas cifra los secretos que quedaron en claro (anteriores al cifrado). Corre en
 * {@link #afterSingletonsInstantiated()}: despues de Flyway (V13 ya amplio la columna) y ANTES de que Tomcat empiece
 * a aceptar requests, asi ningun login con 2FA encuentra un secreto a medio migrar.</p>
 */
@Configuration
public class CifradoTotpConfig implements SmartInitializingSingleton {

    private final MigracionTotpService migracion;

    CifradoTotpConfig(CifradorTotp cifrador, MigracionTotpService migracion) {
        if (!cifrador.disponible()) {
            throw new IllegalStateException(
                    "Falta app.seguridad.totp-cifrado-key (variable TOTP_CIFRADO_KEY): Base64 de 32 bytes, p. ej. openssl rand -base64 32");
        }
        this.migracion = migracion;
    }

    @Override
    public void afterSingletonsInstantiated() {
        migracion.cifrarSecretosEnClaro();
    }
}
