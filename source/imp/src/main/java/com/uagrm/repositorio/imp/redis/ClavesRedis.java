package com.uagrm.repositorio.imp.redis;

/**
 * Unico lugar donde se construyen las keys de Redis. Cada microservicio usa su propio namespace.
 */
public final class ClavesRedis {

    private static final String AUTH = "auth:";
    private static final String CATALOGO = "catalog:cache:";

    private ClavesRedis() {
    }

    /**
     * catalog:cache:{entidad}:todas o catalog:cache:{entidad}:facultad:{idFacultad} → JSON del listado de activos.
     * TTL: app.catalogo.cache.ttl (600 s).
     */
    public static String catalogo(String entidad, Integer idFacultad) {
        return CATALOGO + entidad + ":" + (idFacultad == null ? "todas" : "facultad:" + idFacultad);
    }

    /**
     * catalog:cache:{entidad}:indice → SET con las keys de listados cacheados de esa entidad.
     * Permite invalidar sin KEYS ni SCAN. TTL: el mismo de la cache (se renueva en cada escritura).
     */
    public static String catalogoIndice(String entidad) {
        return CATALOGO + entidad + ":indice";
    }

    /** auth:blacklist:{jti} → "revoked". TTL: vida restante del token. */
    public static String blacklist(String jti) {
        return AUTH + "blacklist:" + jti;
    }

    /** auth:revocado-desde:{idUsuario} → epoch milisegundos. Tokens emitidos antes quedan invalidos. TTL: refresh TTL. */
    public static String revocadoDesde(String idUsuario) {
        return AUTH + "revocado-desde:" + idUsuario;
    }

    /** auth:2fa:pending:{tokenTemporal} → idUsuario. TTL: 300 s. */
    public static String dosFactoresPendiente(String tokenTemporal) {
        return AUTH + "2fa:pending:" + tokenTemporal;
    }

    /** auth:2fa:intentos:{tokenTemporal} → contador. TTL: 300 s. */
    public static String dosFactoresIntentos(String tokenTemporal) {
        return AUTH + "2fa:intentos:" + tokenTemporal;
    }
}
