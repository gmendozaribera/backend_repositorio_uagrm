package com.uagrm.repositorio.imp.service;

import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dao.repository.PersonaRepository;
import com.uagrm.repositorio.dao.repository.TipoUsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.imp.config.AdminInicialProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer ADMIN al arrancar si todavia no existe, para poder iniciar sesion en un sistema vacio.
 * La contrasena viene de ADMIN_INICIAL_PASSWORD (nunca del codigo) y se obliga a cambiarla en el primer login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.admin-inicial", name = "habilitado", havingValue = "true")
public class AdminInicialService implements ApplicationRunner {

    private final AdminInicialProperties props;
    private final UsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByUsuario(props.usuario())) {
            return;
        }
        TipoUsuario admin = tipoUsuarioRepository.findByNombre(TipoUsuario.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Falta el tipo de usuario ADMIN (migracion V2)"));

        Persona persona = new Persona();
        persona.setNombre("Administrador");
        persona.setApellidos("del Sistema");
        persona.setCi(props.ci());
        persona.setCorreo(props.correo());
        personaRepository.save(persona);

        Usuario usuario = new Usuario();
        usuario.setUsuario(props.usuario());
        usuario.setPassword(passwordEncoder.encode(props.password()));
        usuario.setRequiereCambioPassword(Boolean.TRUE);
        usuario.setTipoUsuario(admin);
        usuario.setPersona(persona);
        usuarioRepository.save(usuario);

        log.warn("Usuario administrador inicial '{}' creado. Debe cambiar la contrasena en el primer inicio de sesion.",
                props.usuario());
    }
}
