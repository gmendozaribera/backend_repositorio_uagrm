package com.uagrm.repositorio.imp.dominio;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra los parametros secretos de la tabla de dominio (p. ej. SMTP_GROUP.PASSWORD) con AES-256-GCM.
 *
 * <p>En terminos simples: la BD y Redis guardan el valor "en una caja fuerte" cuya llave NO esta en la BD
 * sino en la variable de entorno {@code DOMINIO_CIFRADO_KEY} (Secret de Kubernetes). Quien robe un respaldo
 * de la BD o lea Redis solo ve texto ilegible. GCM ademas detecta si alguien altero el texto cifrado
 * (el descifrado falla en vez de devolver basura).</p>
 *
 * <p>Formato guardado: {@code v1:<Base64(iv || cifrado+tag)>}. El IV (12 bytes) es aleatorio en cada cifrado,
 * asi dos valores iguales nunca producen el mismo texto cifrado. "v1" permite cambiar el algoritmo en el futuro
 * sin romper los valores ya guardados. Un valor vacio se guarda vacio (= secreto sin configurar).</p>
 *
 * <p>La llave es opcional en este bean: solo user-service la exige al arrancar (es el unico que escribe y usa
 * secretos). Los demas servicios no la reciben (principio de minimo privilegio) y si intentaran descifrar
 * obtendrian un IllegalStateException. Nunca se loguea ni el valor plano ni el cifrado.</p>
 */
@Component
public class CifradorDominio {

    public static final String PREFIJO = "v1:";

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LARGO_LLAVE = 32;
    private static final int LARGO_IV = 12;
    private static final int LARGO_TAG_BITS = 128;

    private final SecretKey llave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorDominio(@Value("${app.dominio.cifrado-key:}") String llaveBase64) {
        this.llave = llaveBase64 == null || llaveBase64.isBlank() ? null : llave(llaveBase64.trim());
    }

    /** true si se configuro app.dominio.cifrado-key. */
    public boolean disponible() {
        return llave != null;
    }

    /** Texto plano → {@code v1:...}; vacio → vacio. */
    public String cifrar(String plano) {
        if (plano == null || plano.isEmpty()) {
            return "";
        }
        byte[] iv = new byte[LARGO_IV];
        aleatorio.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, llaveObligatoria(), new GCMParameterSpec(LARGO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));
            byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el parametro secreto", ex);
        }
    }

    /**
     * {@code v1:...} → texto plano; vacio → vacio. Falla (IllegalStateException, sin el valor en el mensaje)
     * si el formato no es valido, la llave no es la que cifro el valor o el dato fue alterado.
     */
    public String descifrar(String guardado) {
        if (guardado == null || guardado.isEmpty()) {
            return "";
        }
        if (!guardado.startsWith(PREFIJO)) {
            throw new IllegalStateException("El parametro secreto no esta cifrado (formato desconocido)");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            if (todo.length <= LARGO_IV) {
                throw new IllegalStateException("El parametro secreto cifrado esta incompleto");
            }
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, llaveObligatoria(), new GCMParameterSpec(LARGO_TAG_BITS, todo, 0, LARGO_IV));
            byte[] plano = cipher.doFinal(todo, LARGO_IV, todo.length - LARGO_IV);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            // Sin la causa: su mensaje no aporta y no debe filtrar nada del valor
            throw new IllegalStateException(
                    "No se pudo descifrar el parametro secreto (la llave DOMINIO_CIFRADO_KEY cambio o el dato fue alterado)");
        }
    }

    private SecretKey llaveObligatoria() {
        if (llave == null) {
            throw new IllegalStateException("Falta app.dominio.cifrado-key (DOMINIO_CIFRADO_KEY) en este servicio");
        }
        return llave;
    }

    private static SecretKey llave(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.dominio.cifrado-key (DOMINIO_CIFRADO_KEY) no es Base64 valido");
        }
        if (bytes.length != LARGO_LLAVE) {
            throw new IllegalStateException(
                    "app.dominio.cifrado-key (DOMINIO_CIFRADO_KEY) debe ser Base64 de exactamente 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
