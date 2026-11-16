package com.uagrm.repositorio.imp.redis;

/**
 * Unico lugar donde se construyen las keys de Redis. Cada microservicio usa su propio namespace.
 */
public final class ClavesRedis {

    private static final String AUTH = "auth:";
    private static final String CATALOGO = "catalog:cache:";
    private static final String DOMINIO = "config:dominio:";
    private static final String CONFIG_FACULTAD = "config:facultad:";
    private static final String NOTIF_PENDIENTES = "notif:pendientes:";

    private ClavesRedis() {
    }

    /**
     * notif:pendientes:{idUsuario} → contador (texto) de notificaciones NO leidas del usuario.
     * TTL: app.notificaciones.cache.ttl (1 dia), renovado en cada escritura. Si no existe se recalcula con un
     * COUNT en PostgreSQL al leerlo; crear una notificacion hace INCR solo si ya existe; marcar leida(s) la borra.
     */
    public static String notificacionesPendientes(Integer idUsuario) {
        return NOTIF_PENDIENTES + idUsuario;
    }

    /**
     * config:dominio:{GRUPO} → HASH domain_name → domain_value con todos los parametros del grupo.
     * TTL: app.dominio.cache.ttl (600 s). Compartida por todos los servicios; se borra en cada escritura.
     */
    public static String dominio(String grupo) {
        return DOMINIO + grupo;
    }

    /**
     * config:facultad:{idFacultad} → JSON de la configuracion PERSONALIZADA de la facultad (fila de config_facultad).
     * TTL: app.config-facultad.cache.ttl (600 s). Las facultades sin configuracion propia no se cachean aqui
     * (usan los defaults, ya cacheados en config:dominio:FACULTAD_DEFAULT_GROUP). Se borra tras cada PUT.
     */
    public static String configFacultad(Integer idFacultad) {
        return CONFIG_FACULTAD + idFacultad;
    }

    /** auth:rate:login:{ip} → intentos de login FALLIDOS desde esa IP. TTL: LOGIN_WAITING minutos desde el primero. */
    public static String limiteLogin(String ip) {
        return AUTH + "rate:login:" + ip;
    }

    /** auth:rate:register:{ip} → intentos de auto-registro FALLIDOS (404/409) desde esa IP. TTL: LOGIN_WAITING minutos. */
    public static String limiteRegistro(String ip) {
        return AUTH + "rate:register:" + ip;
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
