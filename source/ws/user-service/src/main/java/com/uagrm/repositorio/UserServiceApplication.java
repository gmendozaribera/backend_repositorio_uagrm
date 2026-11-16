package com.uagrm.repositorio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Vive en el paquete raiz com.uagrm.repositorio para que el escaneo de componentes,
 * entidades y repositorios cubra los modulos compartidos dao, dto e imp.
 * Solo expone sus propios controllers: los de otros servicios viven en otros modulos ws y no estan en su classpath.
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
