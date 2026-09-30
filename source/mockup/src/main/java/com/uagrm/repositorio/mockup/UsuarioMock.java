package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Persona;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;

/** Builders de usuarios de prueba. */
public final class UsuarioMock {

    public static final String PASSWORD_HASH = "$2a$10$hashDePruebaNoSeUsaConBCryptReal";

    private UsuarioMock() {
    }

    public static Usuario estudiante() {
        return usuario(1, "est.juan", TipoUsuario.ESTUDIANTE);
    }

    public static Usuario usuario(Integer id, String nombreUsuario, String rol) {
        TipoUsuario tipo = new TipoUsuario();
        tipo.setIdTipoUsuario(1);
        tipo.setNombre(rol);

        Persona persona = new Persona();
        persona.setIdPersona(id);
        persona.setNombre("Juan");
        persona.setApellidos("Perez");
        persona.setCi(7654321);
        persona.setCorreo(nombreUsuario + "@uagrm.edu.bo");

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setUsuario(nombreUsuario);
        usuario.setPassword(PASSWORD_HASH);
        usuario.setRequiereCambioPassword(Boolean.FALSE);
        usuario.setTwoFactorEnabled(Boolean.FALSE);
        usuario.setTipoUsuario(tipo);
        usuario.setPersona(persona);
        usuario.setEstado(Estado.ACTIVO);
        return usuario;
    }
}
