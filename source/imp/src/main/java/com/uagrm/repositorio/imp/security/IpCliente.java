package com.uagrm.repositorio.imp.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;

/**
 * Normaliza la IP del cliente para usarla en las keys del limite de intentos (auth:rate:*:{ip}).
 * La IP llega de request.getRemoteAddr(): con server.forward-headers-strategy=native, Tomcat ya la tomo de
 * X-Forwarded-For SOLO si el request vino de un proxy interno (nginx); un cliente no puede falsificarla.
 * <ul>
 *   <li>IPv4: tal cual (192.168.1.10).</li>
 *   <li>IPv4 escrita como IPv6 (::ffff:192.168.1.10): se trata como la IPv4.</li>
 *   <li>IPv6: se agrupa por el prefijo /64 (2001:db8:1:2::/64). Un usuario recibe un /64 entero de su proveedor y
 *       puede cambiar los ultimos 64 bits a voluntad: contar por direccion exacta le daria intentos infinitos.</li>
 * </ul>
 */
public final class IpCliente {

    private static final byte[] PREFIJO_IPV4_MAPEADA = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff};

    private IpCliente() {
    }

    public static String normalizar(String ip) {
        if (ip == null || ip.isBlank()) {
            return "desconocida";
        }
        String limpia = ip.trim();
        if (limpia.startsWith("[") && limpia.endsWith("]")) {
            limpia = limpia.substring(1, limpia.length() - 1);
        }
        int zona = limpia.indexOf('%');  // fe80::1%eth0
        if (zona >= 0) {
            limpia = limpia.substring(0, zona);
        }
        InetAddress direccion;
        try {
            // ofLiteral no consulta DNS: solo acepta una IP escrita
            direccion = InetAddress.ofLiteral(limpia);
        } catch (IllegalArgumentException ex) {
            return limpia.toLowerCase(Locale.ROOT);
        }
        if (direccion instanceof Inet4Address) {
            return direccion.getHostAddress();
        }
        byte[] bytes = direccion.getAddress();
        if (Arrays.equals(Arrays.copyOf(bytes, PREFIJO_IPV4_MAPEADA.length), PREFIJO_IPV4_MAPEADA)) {
            return (bytes[12] & 0xff) + "." + (bytes[13] & 0xff) + "." + (bytes[14] & 0xff) + "." + (bytes[15] & 0xff);
        }
        StringBuilder prefijo = new StringBuilder();
        for (int i = 0; i < 8; i += 2) {
            prefijo.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
        }
        return prefijo.append(":/64").toString();
    }
}
