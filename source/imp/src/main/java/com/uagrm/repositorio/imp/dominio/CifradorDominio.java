package com.uagrm.repositorio.imp.dominio;

import com.uagrm.repositorio.imp.security.CifradorAesGcm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra los parametros secretos de la tabla de dominio (p. ej. SMTP_GROUP.PASSWORD) con AES-256-GCM
 * ({@link CifradorAesGcm}: formato {@code v1:...}, IV aleatorio, deteccion de alteraciones).
 *
 * <p>En terminos simples: la BD y Redis guardan el valor "en una caja fuerte" cuya llave NO esta en la BD
 * sino en la variable de entorno {@code DOMINIO_CIFRADO_KEY} (Secret de Kubernetes). Quien robe un respaldo
 * de la BD o lea Redis solo ve texto ilegible.</p>
 *
 * <p>La llave es opcional en este bean: solo user-service la exige al arrancar (es el unico que escribe y usa
 * secretos). Los demas servicios no la reciben (principio de minimo privilegio) y si intentaran descifrar
 * obtendrian un IllegalStateException. Nunca se loguea ni el valor plano ni el cifrado.</p>
 */
@Component
public class CifradorDominio extends CifradorAesGcm {

    public CifradorDominio(@Value("${app.dominio.cifrado-key:}") String llaveBase64) {
        super(llaveBase64, "app.dominio.cifrado-key", "DOMINIO_CIFRADO_KEY", "parametro secreto");
    }
}
