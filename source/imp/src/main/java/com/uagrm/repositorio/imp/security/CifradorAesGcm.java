package com.uagrm.repositorio.imp.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado AES-256-GCM de un texto corto guardado en la BD, con una llave que vive FUERA de la BD (variable de
 * entorno / Secret de Kubernetes). Base comun de {@code CifradorDominio} (parametros secretos de la tabla dominio)
 * y {@link CifradorTotp} (secreto TOTP de cada usuario): cada uno con SU llave, asi filtrar una no abre la otra.
 *
 * <p>En terminos simples: la BD guarda el valor "en una caja fuerte" y la llave la tiene solo el servicio que lo
 * necesita. Quien robe un respaldo de la BD solo ve texto ilegible. GCM ademas detecta si alguien altero el texto
 * cifrado (el descifrado falla en vez de devolver basura).</p>
 *
 * <p>Formato guardado: {@code v1:<Base64(iv || cifrado+tag)>}. El IV (12 bytes) es aleatorio en cada cifrado, asi
 * dos valores iguales nunca producen el mismo texto cifrado. "v1" permite cambiar el algoritmo en el futuro sin
 * romper los valores ya guardados. Un valor vacio se guarda vacio. Los mensajes de error nunca incluyen el valor
 * (ni plano ni cifrado).</p>
 */
public abstract class CifradorAesGcm {

    public static final String PREFIJO = "v1:";

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LARGO_LLAVE = 32;
    private static final int LARGO_IV = 12;
    private static final int LARGO_TAG_BITS = 128;

    private final SecretKey llave;
    private final SecureRandom aleatorio = new SecureRandom();
    /** "app.dominio.cifrado-key (DOMINIO_CIFRADO_KEY)": para los mensajes de error. */
    private final String nombreLlave;
    private final String variable;
    /** "parametro secreto", "secreto TOTP": que se cifra, para los mensajes de error. */
    private final String dato;

    /**
     * @param llaveBase64 Base64 de 32 bytes, o vacio/null (sin llave: {@link #disponible()} false)
     * @param propiedad   propiedad de Spring de la llave (solo para los mensajes)
     * @param variable    variable de entorno de la llave (solo para los mensajes)
     * @param dato        que se cifra, en minusculas (solo para los mensajes)
     */
    protected CifradorAesGcm(String llaveBase64, String propiedad, String variable, String dato) {
        this.nombreLlave = propiedad + " (" + variable + ")";
        this.variable = variable;
        this.dato = dato;
        this.llave = llaveBase64 == null || llaveBase64.isBlank() ? null : llave(llaveBase64.trim());
    }

    /** true si se configuro la llave. */
    public boolean disponible() {
        return llave != null;
    }

    /** true si el valor ya tiene el formato cifrado ({@code v1:...}). No verifica que se pueda descifrar. */
    public static boolean estaCifrado(String guardado) {
        return guardado != null && guardado.startsWith(PREFIJO);
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
            throw new IllegalStateException("No se pudo cifrar el " + dato, ex);
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
        if (!estaCifrado(guardado)) {
            throw new IllegalStateException("El " + dato + " no esta cifrado (formato desconocido)");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            if (todo.length <= LARGO_IV) {
                throw new IllegalStateException("El " + dato + " cifrado esta incompleto");
            }
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, llaveObligatoria(), new GCMParameterSpec(LARGO_TAG_BITS, todo, 0, LARGO_IV));
            byte[] plano = cipher.doFinal(todo, LARGO_IV, todo.length - LARGO_IV);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            // Sin la causa: su mensaje no aporta y no debe filtrar nada del valor
            throw new IllegalStateException("No se pudo descifrar el " + dato + " (la llave " + variable
                    + " cambio o el dato fue alterado)");
        }
    }

    private SecretKey llaveObligatoria() {
        if (llave == null) {
            throw new IllegalStateException("Falta " + nombreLlave + " en este servicio");
        }
        return llave;
    }

    private SecretKey llave(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(nombreLlave + " no es Base64 valido");
        }
        if (bytes.length != LARGO_LLAVE) {
            throw new IllegalStateException(nombreLlave + " debe ser Base64 de exactamente 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
