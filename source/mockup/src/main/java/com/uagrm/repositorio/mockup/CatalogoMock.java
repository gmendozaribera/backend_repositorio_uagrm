package com.uagrm.repositorio.mockup;

import com.uagrm.repositorio.dao.entity.Carrera;
import com.uagrm.repositorio.dao.entity.Estado;
import com.uagrm.repositorio.dao.entity.Facultad;
import com.uagrm.repositorio.dao.entity.Fase;
import com.uagrm.repositorio.dao.entity.Grupo;
import com.uagrm.repositorio.dao.entity.Materia;
import com.uagrm.repositorio.dao.entity.Semestre;
import com.uagrm.repositorio.dao.entity.TipoTrabajo;
import com.uagrm.repositorio.dao.entity.TipoUsuario;
import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.dto.catalogo.CarreraResponse;
import com.uagrm.repositorio.dto.catalogo.DocenteGrupoResponse;
import com.uagrm.repositorio.dto.catalogo.FacultadResponse;
import com.uagrm.repositorio.dto.catalogo.FaseResponse;
import com.uagrm.repositorio.dto.catalogo.GrupoResponse;
import com.uagrm.repositorio.dto.catalogo.MateriaResponse;
import com.uagrm.repositorio.dto.catalogo.SemestreResponse;
import com.uagrm.repositorio.dto.catalogo.TipoTrabajoResponse;

/** Builders de catalogos de prueba (entidades y responses) con datos de la FICCT. */
public final class CatalogoMock {

    private CatalogoMock() {
    }

    // ---------- Entidades ----------

    public static Facultad facultad() {
        return facultad(1, "FICCT");
    }

    public static Facultad facultad(Integer id, String sigla) {
        Facultad facultad = new Facultad();
        facultad.setIdFacultad(id);
        facultad.setNombre("Facultad " + sigla);
        facultad.setSigla(sigla);
        facultad.setEstado(Estado.ACTIVO);
        return facultad;
    }

    public static Carrera carrera(Facultad facultad) {
        Carrera carrera = new Carrera();
        carrera.setIdCarrera(10);
        carrera.setNombre("Ingenieria Informatica");
        carrera.setSigla("INF");
        carrera.setFacultad(facultad);
        carrera.setEstado(Estado.ACTIVO);
        return carrera;
    }

    public static Materia materia(Facultad facultad) {
        Materia materia = new Materia();
        materia.setIdMateria(20);
        materia.setNombre("Ingenieria de Software I");
        materia.setSigla("INF412");
        materia.setFacultad(facultad);
        materia.setEstado(Estado.ACTIVO);
        return materia;
    }

    public static Semestre semestre(Facultad facultad) {
        Semestre semestre = new Semestre();
        semestre.setIdSemestre(30);
        semestre.setSemestre(2);
        semestre.setAnio(2026);
        semestre.setFacultad(facultad);
        semestre.setEstado(Estado.ACTIVO);
        return semestre;
    }

    public static Grupo grupo(Materia materia, Semestre semestre) {
        Grupo grupo = new Grupo();
        grupo.setIdGrupo(40);
        grupo.setNombre("SA");
        grupo.setMateria(materia);
        grupo.setSemestre(semestre);
        grupo.setEstado(Estado.ACTIVO);
        return grupo;
    }

    public static Fase fase(Facultad facultad) {
        Fase fase = new Fase();
        fase.setIdFase(50);
        fase.setNombre("Perfil");
        fase.setDescripcion("Propuesta inicial del trabajo");
        fase.setFacultad(facultad);
        fase.setEstado(Estado.ACTIVO);
        return fase;
    }

    public static TipoTrabajo tipoTrabajo(Facultad facultad) {
        TipoTrabajo tipo = new TipoTrabajo();
        tipo.setIdTipoTrabajo(60);
        tipo.setNombre("Proyecto de grado");
        tipo.setFacultad(facultad);
        tipo.setEstado(Estado.ACTIVO);
        return tipo;
    }

    public static Usuario docente() {
        return UsuarioMock.usuario(5, "doc.maria", TipoUsuario.DOCENTE);
    }

    // ---------- Responses ----------

    public static FacultadResponse facultadResponse() {
        return new FacultadResponse(1, "Facultad de Ingenieria en Ciencias de la Computacion", "FICCT", Estado.ACTIVO);
    }

    public static CarreraResponse carreraResponse() {
        return new CarreraResponse(10, "Ingenieria Informatica", "INF", 1, Estado.ACTIVO);
    }

    public static MateriaResponse materiaResponse() {
        return new MateriaResponse(20, "Ingenieria de Software I", "INF412", 1, Estado.ACTIVO);
    }

    public static SemestreResponse semestreResponse() {
        return new SemestreResponse(30, 2, 2026, 1, Estado.ACTIVO);
    }

    public static GrupoResponse grupoResponse() {
        return new GrupoResponse(40, "SA", 20, 30, Estado.ACTIVO);
    }

    public static DocenteGrupoResponse docenteGrupoResponse() {
        return new DocenteGrupoResponse(40, 5, "doc.maria", "Maria", "Lopez", true);
    }

    public static FaseResponse faseResponse() {
        return new FaseResponse(50, "Perfil", null, 1, Estado.ACTIVO);
    }

    public static TipoTrabajoResponse tipoTrabajoResponse() {
        return new TipoTrabajoResponse(60, "Proyecto de grado", 1, Estado.ACTIVO);
    }
}
