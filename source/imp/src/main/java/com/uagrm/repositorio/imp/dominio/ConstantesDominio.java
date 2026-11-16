package com.uagrm.repositorio.imp.dominio;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Unico lugar del codigo donde se nombran los parametros de la tabla de dominio.
 *
 * <p><b>Regla 11 (CLAUDE.md):</b> todo parametro configurable del sistema se registra en la tabla {@code dominio}
 * ({@code domain_group}, {@code domain_name}, {@code domain_value}, {@code description}) con una migracion Flyway
 * que lo siembra, y su grupo/nombre se declaran SOLO aqui (nunca strings sueltos en otras clases). Se lee con
 * {@link ParametrosService} (cache Redis {@code config:dominio:{GRUPO}}, TTL 600 s) y se administra con
 * {@code /api/v1/dominios} (user-service). Excepcion: la infraestructura (URLs, credenciales, secretos, puertos)
 * sigue en variables de entorno.</p>
 *
 * <p>Para agregar un parametro: 1) migracion que inserta la fila; 2) constante de grupo/nombre aqui;
 * 3) entrada en {@link Parametro} con su tipo, rango y valor por defecto; 4) leerlo con
 * {@code parametrosService.entero(Parametro.X)} (o booleano/texto/secreto segun el tipo). Con "Find usages"
 * sobre la entrada de {@link Parametro} se ve que clases usan cada parametro.</p>
 */
public final class ConstantesDominio {

    // ---------- Grupos ----------

    /** Autenticacion: limite de intentos fallidos de login y auto-registro. */
    public static final String GRUPO_AUTH = "AUTH_GROUP";
    /** Valores por defecto de config_facultad para las facultades que aun no guardaron su configuracion. */
    public static final String GRUPO_FACULTAD_DEFAULT = "FACULTAD_DEFAULT_GROUP";
    /** Servidor de correo saliente (antes tabla config_smtp; desde V6 vive aqui). */
    public static final String GRUPO_SMTP = "SMTP_GROUP";

    // ---------- Nombres: AUTH_GROUP ----------

    /** Intentos fallidos permitidos por IP (login y auto-registro) antes de bloquear. */
    public static final String LOGIN_INTENTOS = "LOGIN_INTENTOS";
    /** Minutos de bloqueo de la IP tras superar LOGIN_INTENTOS. */
    public static final String LOGIN_WAITING = "LOGIN_WAITING";

    // ---------- Nombres: FACULTAD_DEFAULT_GROUP (mismos rangos que los CHECK de config_facultad) ----------

    public static final String TAMANO_MAX_ARCHIVO_MB = "TAMANO_MAX_ARCHIVO_MB";
    public static final String UMBRAL_PLAGIO_MEDIO = "UMBRAL_PLAGIO_MEDIO";
    public static final String UMBRAL_PLAGIO_ALTO = "UMBRAL_PLAGIO_ALTO";
    public static final String COMPARACION_INTERNET = "COMPARACION_INTERNET";
    public static final String ACCESO_EXTERNO = "ACCESO_EXTERNO";
    public static final String ESCALA_CALIF_MAX = "ESCALA_CALIF_MAX";
    public static final String JWT_EXPIRACION_HORAS = "JWT_EXPIRACION_HORAS";

    // ---------- Nombres: SMTP_GROUP ----------

    public static final String HOST = "HOST";
    public static final String PUERTO = "PUERTO";
    public static final String CORREO = "CORREO";
    /** Secreto: se guarda cifrado (AES-GCM) y la API nunca lo devuelve. */
    public static final String PASSWORD = "PASSWORD";
    public static final String SEGURIDAD = "SEGURIDAD";
    public static final String ACTIVO = "ACTIVO";

    /** Valores de SMTP_GROUP.SEGURIDAD. */
    public static final String SEGURIDAD_SSL = "SSL";
    public static final String SEGURIDAD_STARTTLS = "STARTTLS";
    public static final String SEGURIDAD_NINGUNA = "NINGUNA";

    /**
     * Largo maximo del texto plano de un secreto: cifrado ocupa ~4/3 * (largo + 28) + 3 caracteres y
     * domain_value admite 500.
     */
    public static final int MAXIMO_BYTES_SECRETO = 300;

    private static final String PATRON_EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String ETIQUETA_HOST = "[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final String PATRON_HOST = "^" + ETIQUETA_HOST + "(\\." + ETIQUETA_HOST + ")*$";

    private ConstantesDominio() {
    }

    /** Tipo del valor (domain_value siempre es texto en la BD). */
    public enum Tipo {
        ENTERO,
        /** 'true' o 'false' (minusculas). */
        BOOLEANO,
        /** Uno de los valores permitidos (MAYUSCULAS). */
        ENUM,
        EMAIL,
        /** Nombre de host o IPv4. */
        HOST,
        /** Texto libre con largo maximo. */
        TEXTO
    }

    /**
     * Definicion de un parametro: tipo, rango (enteros) o largo maximo (textos), valores permitidos (enum),
     * valor por defecto, si es secreto y si admite valor vacio.
     */
    public record Definicion(String grupo, String nombre, Tipo tipo, long minimo, long maximo,
                             List<String> valores, String porDefecto, boolean secreto, boolean admiteVacio) {
    }

    private static Definicion entero(String grupo, String nombre, long minimo, long maximo, long porDefecto) {
        return new Definicion(grupo, nombre, Tipo.ENTERO, minimo, maximo, List.of(), Long.toString(porDefecto),
                false, false);
    }

    private static Definicion booleano(String grupo, String nombre, boolean porDefecto) {
        return new Definicion(grupo, nombre, Tipo.BOOLEANO, 0, 0, List.of(), Boolean.toString(porDefecto), false, false);
    }

    private static Definicion enumerado(String grupo, String nombre, String porDefecto, String... valores) {
        return new Definicion(grupo, nombre, Tipo.ENUM, 0, 0, List.of(valores), porDefecto, false, false);
    }

    private static Definicion texto(String grupo, String nombre, Tipo tipo, long largoMaximo, String porDefecto,
                                    boolean secreto, boolean admiteVacio) {
        return new Definicion(grupo, nombre, tipo, 0, largoMaximo, List.of(), porDefecto, secreto, admiteVacio);
    }

    /**
     * Parametros que usa el sistema: no se pueden eliminar y su valor se valida al editarlo.
     * El valor por defecto solo se usa como respaldo si la fila no existe o su valor es invalido (con log warn).
     */
    public enum Parametro {

        AUTH_LOGIN_INTENTOS(entero(GRUPO_AUTH, LOGIN_INTENTOS, 1, 1000, 10)),
        AUTH_LOGIN_WAITING(entero(GRUPO_AUTH, LOGIN_WAITING, 1, 1440, 15)),

        FACULTAD_TAMANO_MAX_ARCHIVO_MB(entero(GRUPO_FACULTAD_DEFAULT, TAMANO_MAX_ARCHIVO_MB, 1, 100, 10)),
        FACULTAD_UMBRAL_PLAGIO_MEDIO(entero(GRUPO_FACULTAD_DEFAULT, UMBRAL_PLAGIO_MEDIO, 0, 100, 30)),
        FACULTAD_UMBRAL_PLAGIO_ALTO(entero(GRUPO_FACULTAD_DEFAULT, UMBRAL_PLAGIO_ALTO, 0, 100, 50)),
        FACULTAD_COMPARACION_INTERNET(booleano(GRUPO_FACULTAD_DEFAULT, COMPARACION_INTERNET, false)),
        FACULTAD_ACCESO_EXTERNO(booleano(GRUPO_FACULTAD_DEFAULT, ACCESO_EXTERNO, false)),
        FACULTAD_ESCALA_CALIF_MAX(entero(GRUPO_FACULTAD_DEFAULT, ESCALA_CALIF_MAX, 1, 1000, 100)),
        FACULTAD_JWT_EXPIRACION_HORAS(entero(GRUPO_FACULTAD_DEFAULT, JWT_EXPIRACION_HORAS, 1, 24, 1)),

        /** Vacio = correo aun sin configurar. */
        SMTP_HOST(texto(GRUPO_SMTP, HOST, Tipo.HOST, 253, "", false, true)),
        SMTP_PUERTO(entero(GRUPO_SMTP, PUERTO, 1, 65535, 587)),
        SMTP_CORREO(texto(GRUPO_SMTP, CORREO, Tipo.EMAIL, 150, "no-responder@uagrm.edu.bo", false, false)),
        /** Secreto (cifrado en la BD); vacio = sin contrasena / sin configurar. */
        SMTP_PASSWORD(texto(GRUPO_SMTP, PASSWORD, Tipo.TEXTO, MAXIMO_BYTES_SECRETO, "", true, true)),
        SMTP_SEGURIDAD(enumerado(GRUPO_SMTP, SEGURIDAD, SEGURIDAD_STARTTLS,
                SEGURIDAD_SSL, SEGURIDAD_STARTTLS, SEGURIDAD_NINGUNA)),
        SMTP_ACTIVO(booleano(GRUPO_SMTP, ACTIVO, false));

        private final Definicion def;

        Parametro(Definicion def) {
            this.def = def;
        }

        public String grupo() {
            return def.grupo();
        }

        public String nombre() {
            return def.nombre();
        }

        public Tipo tipo() {
            return def.tipo();
        }

        /** Valor por defecto como texto (igual que se guarda en domain_value). */
        public String porDefecto() {
            return def.porDefecto();
        }

        /** true si se guarda cifrado y la API nunca lo devuelve. */
        public boolean secreto() {
            return def.secreto();
        }

        public boolean admiteVacio() {
            return def.admiteVacio();
        }

        /**
         * Mensaje de error si el valor no cumple el tipo/rango; vacio si es valido.
         * En un secreto se valida el texto plano (antes de cifrar).
         */
        public Optional<String> validar(String valor) {
            if (valor == null || valor.isEmpty()) {
                return def.admiteVacio() ? Optional.empty() : Optional.of("no puede estar vacio");
            }
            return switch (def.tipo()) {
                case ENTERO -> validarEntero(valor);
                case BOOLEANO -> "true".equals(valor) || "false".equals(valor)
                        ? Optional.empty() : Optional.of("debe ser 'true' o 'false'");
                case ENUM -> def.valores().contains(valor)
                        ? Optional.empty() : Optional.of("debe ser uno de: " + String.join(", ", def.valores()));
                case EMAIL -> valor.length() <= def.maximo() && valor.matches(PATRON_EMAIL)
                        ? Optional.empty() : Optional.of("debe ser un correo valido de hasta " + def.maximo() + " caracteres");
                case HOST -> valor.length() <= def.maximo() && valor.matches(PATRON_HOST)
                        ? Optional.empty() : Optional.of("debe ser un nombre de servidor valido (p. ej. smtp.gmail.com)");
                case TEXTO -> valor.getBytes(StandardCharsets.UTF_8).length <= def.maximo()
                        ? Optional.empty() : Optional.of("debe tener como maximo " + def.maximo() + " bytes");
            };
        }

        private Optional<String> validarEntero(String valor) {
            String error = "debe ser un numero entero entre " + def.minimo() + " y " + def.maximo();
            if (!valor.trim().matches("-?\\d{1,18}")) {
                return Optional.of(error);
            }
            long numero = Long.parseLong(valor.trim());
            return numero < def.minimo() || numero > def.maximo() ? Optional.of(error) : Optional.empty();
        }
    }

    /** El parametro del sistema con ese grupo y nombre (ya en MAYUSCULAS), si existe. */
    public static Optional<Parametro> buscar(String grupo, String nombre) {
        return Arrays.stream(Parametro.values())
                .filter(p -> p.grupo().equals(grupo) && p.nombre().equals(nombre))
                .findFirst();
    }

    /** true si el codigo usa este parametro (respuesta {@code sistema} de /api/v1/dominios). */
    public static boolean esDelSistema(String grupo, String nombre) {
        return buscar(grupo, nombre).isPresent();
    }

    /** true si el parametro se guarda cifrado (respuesta {@code secreto} de /api/v1/dominios). */
    public static boolean esSecreto(String grupo, String nombre) {
        return buscar(grupo, nombre).map(Parametro::secreto).orElse(false);
    }
}
