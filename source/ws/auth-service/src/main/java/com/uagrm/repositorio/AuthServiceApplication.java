package com.uagrm.repositorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Vive en el paquete raiz com.uagrm.repositorio para que el escaneo de componentes,
 * entidades y repositorios cubra los modulos compartidos dao, dto e imp.
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
