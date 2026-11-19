package com.uagrm.repositorio.imp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra el secreto TOTP de cada usuario ({@code usuario.totp_secret}) con AES-256-GCM y una llave propia,
 * {@code TOTP_CIFRADO_KEY} (distinta de la de la tabla dominio).
 *
 * <p>En terminos simples: el secreto TOTP es la "semilla" con la que el celular del usuario genera sus codigos de 6
 * digitos. Si alguien robara un respaldo de la BD con las semillas en claro, podria generar los codigos de todos y
 * la verificacion en dos pasos dejaria de servir. Cifradas, el respaldo no alcanza: tambien necesitaria la llave,
 * que no esta en la BD.</p>
 *
 * <p>Solo auth-service recibe la llave (es el unico que lee y escribe el secreto TOTP) y no arranca sin ella; los
 * demas servicios no la tienen (minimo privilegio). Nunca se loguea ni el secreto ni su version cifrada.</p>
 */
@Component
public class CifradorTotp extends CifradorAesGcm {

    public CifradorTotp(@Value("${app.seguridad.totp-cifrado-key:}") String llaveBase64) {
        super(llaveBase64, "app.seguridad.totp-cifrado-key", "TOTP_CIFRADO_KEY", "secreto TOTP");
    }
}
