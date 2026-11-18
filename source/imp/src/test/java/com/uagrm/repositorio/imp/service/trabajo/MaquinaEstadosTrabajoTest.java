package com.uagrm.repositorio.imp.service.trabajo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.uagrm.repositorio.dao.entity.EstadoTrabajo;
import com.uagrm.repositorio.dao.entity.TransicionTrabajo;
import com.uagrm.repositorio.dao.entity.TransicionTrabajo.Actor;
import com.uagrm.repositorio.imp.exception.NegocioException;
import com.uagrm.repositorio.mockup.TrabajoMock;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/** Maquina de estados del trabajo: la tabla completa de transiciones permitidas y prohibidas (contrato work-api). */
class MaquinaEstadosTrabajoTest {

    @Test
    void transicionesDelContrato() {
        assertTransicion(TransicionTrabajo.ENVIAR, Actor.AUTOR, EstadoTrabajo.PENDIENTE,
                EstadoTrabajo.BORRADOR, EstadoTrabajo.RECHAZADO, EstadoTrabajo.APROBADO_CON_OBSERVACIONES);
        assertTransicion(TransicionTrabajo.TOMAR_REVISION, Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.EN_REVISION,
                EstadoTrabajo.PENDIENTE);
        assertTransicion(TransicionTrabajo.APROBAR, Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.APROBADO,
                EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION);
        assertTransicion(TransicionTrabajo.APROBAR_CON_OBSERVACIONES, Actor.DOCENTE_DEL_GRUPO,
                EstadoTrabajo.APROBADO_CON_OBSERVACIONES, EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION);
        assertTransicion(TransicionTrabajo.RECHAZAR, Actor.DOCENTE_DEL_GRUPO, EstadoTrabajo.RECHAZADO,
                EstadoTrabajo.PENDIENTE, EstadoTrabajo.EN_REVISION);
    }

    @Test
    void aprobadoYEvaluadoSonFinalesEnElSprint3() {
        for (TransicionTrabajo t : TransicionTrabajo.values()) {
            assertThat(t.aplicableDesde(EstadoTrabajo.APROBADO)).as(t.name()).isFalse();
            assertThat(t.aplicableDesde(EstadoTrabajo.EVALUADO)).as(t.name()).isFalse();
        }
    }

    @Test
    void soloSonEditablesBorradorRechazadoYAprobadoConObservaciones() {
        Set<EstadoTrabajo> editables = EnumSet.noneOf(EstadoTrabajo.class);
        for (EstadoTrabajo e : EstadoTrabajo.values()) {
            if (e.editable()) {
                editables.add(e);
            }
        }
        assertThat(editables).containsExactlyInAnyOrder(EstadoTrabajo.BORRADOR, EstadoTrabajo.RECHAZADO,
                EstadoTrabajo.APROBADO_CON_OBSERVACIONES);
    }

    @ParameterizedTest
    @EnumSource(EstadoTrabajo.class)
    void exigirTransicionDevuelve409FueraDeSusOrigenes(EstadoTrabajo estado) {
        for (TransicionTrabajo t : TransicionTrabajo.values()) {
            var trabajo = TrabajoMock.trabajo(estado);
            if (t.aplicableDesde(estado)) {
                assertThatNoException().isThrownBy(() -> TrabajoService.exigirTransicion(trabajo, t));
            } else {
                assertThatThrownBy(() -> TrabajoService.exigirTransicion(trabajo, t))
                        .isInstanceOf(NegocioException.class)
                        .hasMessageContaining(estado.name())
                        .extracting("status").isEqualTo(HttpStatus.CONFLICT);
            }
        }
    }

    private static void assertTransicion(TransicionTrabajo t, Actor actor, EstadoTrabajo destino,
                                         EstadoTrabajo... origenes) {
        assertThat(t.actor()).isEqualTo(actor);
        assertThat(t.destino()).isEqualTo(destino);
        assertThat(t.origenes()).containsExactlyInAnyOrder(origenes);
        for (EstadoTrabajo e : EstadoTrabajo.values()) {
            assertThat(t.aplicableDesde(e)).as(t + " desde " + e).isEqualTo(Set.of(origenes).contains(e));
        }
    }
}
