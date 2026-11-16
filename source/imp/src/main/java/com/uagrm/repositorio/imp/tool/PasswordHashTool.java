package com.uagrm.repositorio.imp.tool;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad de desarrollador para trabajar con los hashes BCrypt de la columna
 * {@code usuario.password}.
 *
 * <p><b>IMPORTANTE:</b> BCrypt es una funcion de una sola via (hash), <b>no cifrado</b>.
 * No existe forma de "desencriptar" el hash y recuperar la contrasena original, ni
 * siquiera para un desarrollador con acceso a la base de datos. Lo unico posible es:
 * <ul>
 *   <li><b>verificar</b> si una contrasena candidata coincide con un hash, o</li>
 *   <li><b>generar</b> el hash de una contrasena nueva para insertarlo a mano.</li>
 * </ul>
 *
 * <p>Esta clase es solo una herramienta de linea de comandos: no es un bean de Spring
 * ni forma parte del arranque de ningun microservicio.
 *
 * <h2>Uso</h2>
 * <pre>
 *   # Verificar: ¿la contrasena coincide con el hash de la tabla?
 *   java ... PasswordHashTool verify 'Admin2026.' '$2a$10$....hashDeLaTabla....'
 *
 *   # Generar: crear un hash BCrypt nuevo para una contrasena
 *   java ... PasswordHashTool hash 'Admin2026.'
 * </pre>
 */
public final class PasswordHashTool {

    /** Mismo encoder que usa la app en produccion ({@code SeguridadConfig#passwordEncoder}). */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordHashTool() {
    }

    public static void main(String[] args) {
//        if (args.length == 0) {
//            imprimirAyuda();
//            return;
//        }
//
//        String comando = args[0].toLowerCase();
//        switch (comando) {
//            case "verify" -> verificar(args);
//            case "hash" -> generar(args);
//            default -> {
//                System.out.println("Comando desconocido: " + comando);
//                imprimirAyuda();
//            }
//        }
        generar("Admin2026.");
    }

    /** Opcion 1: comprueba si una contrasena candidata produce el hash dado. */
    private static void verificar(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: verify <contrasenaCandidata> <hashBcrypt>");
            return;
        }
        String candidata = args[1];
        String hash = args[2];

        boolean coincide = ENCODER.matches(candidata, hash);

        System.out.println("=== Verificacion BCrypt ===");
        System.out.println("Candidata : " + candidata);
        System.out.println("Hash      : " + hash);
        System.out.println("Resultado : " + (coincide
                ? "COINCIDE  -> esta es la contrasena del usuario"
                : "NO coincide -> la contrasena probada no es la correcta"));
    }

    /** Opcion 3: genera un hash BCrypt nuevo para insertarlo en la tabla usuario. */
    private static void generar(String password) {
        if (password.length() < 2) {
            System.out.println("Uso: hash <contrasena>");
            return;
        }
        String hash = ENCODER.encode(password);

        System.out.println("=== Generacion de hash BCrypt ===");
        System.out.println("Contrasena : " + password);
        System.out.println("Hash       : " + hash);
        System.out.println();
        System.out.println("SQL de ejemplo:");
        System.out.println("  UPDATE usuario SET password = '" + hash + "' WHERE correo = '...';");
    }

    private static void imprimirAyuda() {
        System.out.println("""
                PasswordHashTool - utilidad BCrypt para desarrollo

                BCrypt NO se puede desencriptar (es hash de una sola via).
                Solo puedes verificar una candidata o generar un hash nuevo.

                Comandos:
                  verify <contrasenaCandidata> <hashBcrypt>   Comprueba si coinciden
                  hash   <contrasena>                         Genera un hash BCrypt nuevo

                Ejemplos:
                  verify 'Admin2026.' '$2a$10$....hashDeLaTabla....'
                  hash   'Admin2026.'
                """);
    }
}
