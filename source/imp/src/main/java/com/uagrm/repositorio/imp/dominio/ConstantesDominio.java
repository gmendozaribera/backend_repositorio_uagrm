package com.uagrm.repositorio.imp.dominio;

import java.util.Arrays;
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
 * {@code parametrosService.entero(Parametro.X)}. Con "Find usages" sobre la entrada de {@link Parametro}
 * se ve que clases usan cada parametro.</p>
 */
public final class ConstantesDominio {

    // ---------- Grupos ----------

    /** Autenticacion: limite de intentos fallidos de login y auto-registro. */
    public static final String GRUPO_AUTH = "AUTH_GROUP";

    // ---------- Nombres ----------

    /** Intentos fallidos permitidos por IP (login y auto-registro) antes de bloquear. */
    public static final String LOGIN_INTENTOS = "LOGIN_INTENTOS";
    /** Minutos de bloqueo de la IP tras superar LOGIN_INTENTOS. */
    public static final String LOGIN_WAITING = "LOGIN_WAITING";

    private ConstantesDominio() {
    }

    /** Tipo del valor (domain_value siempre es texto en la BD). */
    public enum Tipo {
        ENTERO
    }

    /**
     * Parametros que usa el sistema: no se pueden eliminar y su valor se valida al editarlo.
     * El valor por defecto solo se usa como respaldo si la fila no existe o su valor es invalido (con log warn).
     */
    public enum Parametro {

        AUTH_LOGIN_INTENTOS(GRUPO_AUTH, LOGIN_INTENTOS, Tipo.ENTERO, 1, 1000, 10),
        AUTH_LOGIN_WAITING(GRUPO_AUTH, LOGIN_WAITING, Tipo.ENTERO, 1, 1440, 15);

        private final String grupo;
        private final String nombre;
        private final Tipo tipo;
        private final long minimo;
        private final long maximo;
        private final long porDefecto;

        Parametro(String grupo, String nombre, Tipo tipo, long minimo, long maximo, long porDefecto) {
            this.grupo = grupo;
            this.nombre = nombre;
            this.tipo = tipo;
            this.minimo = minimo;
            this.maximo = maximo;
            this.porDefecto = porDefecto;
        }

        public String grupo() {
            return grupo;
        }

        public String nombre() {
            return nombre;
        }

        public Tipo tipo() {
            return tipo;
        }

        public long porDefecto() {
            return porDefecto;
        }

        /** Mensaje de error si el valor no cumple el tipo/rango; vacio si es valido. */
        public Optional<String> validar(String valor) {
            return switch (tipo) {
                case ENTERO -> validarEntero(valor);
            };
        }

        private Optional<String> validarEntero(String valor) {
            String error = "debe ser un numero entero entre " + minimo + " y " + maximo;
            if (valor == null || !valor.trim().matches("-?\\d{1,18}")) {
                return Optional.of(error);
            }
            long numero = Long.parseLong(valor.trim());
            return numero < minimo || numero > maximo ? Optional.of(error) : Optional.empty();
        }
    }

    /** El parametro del sistema con ese grupo y nombre (ya en MAYUSCULAS), si existe. */
    public static Optional<Parametro> buscar(String grupo, String nombre) {
        return Arrays.stream(Parametro.values())
                .filter(p -> p.grupo.equals(grupo) && p.nombre.equals(nombre))
                .findFirst();
    }

    /** true si el codigo usa este parametro (respuesta {@code sistema} de /api/v1/dominios). */
    public static boolean esDelSistema(String grupo, String nombre) {
        return buscar(grupo, nombre).isPresent();
    }
}
