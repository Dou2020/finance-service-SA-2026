package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.PruebaDeIntegracion;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * fleet (S6) caído, lento o respondiendo 4xx. Comparte el contexto con las demás pruebas, así que
 * cierra el circuito al terminar: abierto, haría fallar con 503 los gastos de las que corran después.
 */
@PruebaDeIntegracion
class FleetCaidoIT {

    private static final String NO_DISPONIBLE =
            "El servicio no está disponible en este momento. Intenta de nuevo en unos minutos";

    @Autowired
    MockMvc mvc;
    @Autowired
    Escenario escenario;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CircuitBreakerRegistry circuitos;

    @AfterEach
    void cerrarElCircuito() {
        circuito().reset();
    }

    @Test
    void unErrorDeFleetSeReintentaUnaVezYDa503SinGuardar() throws Exception {
        UUID sucursal = escenario.sucursal();
        UUID busId = UUID.randomUUID();
        escenario.fleetResponde(busId, 500, "Error interno");

        registrar(sucursal, busId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(NO_DISPONIBLE));
        assertThat(escenario.llamadasAFleet(busId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from gasto_taller where bus_id = ?", Integer.class, busId))
                .isZero();
    }

    @Test
    void fleetLentoAgotaElTimeoutYDa503() throws Exception {
        UUID busId = UUID.randomUUID();
        escenario.fleetTarda(busId, 3_000);

        registrar(escenario.sucursal(), busId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(NO_DISPONIBLE));
    }

    @Test
    void cincoFallosSeguidosAbrenElCircuitoYYaNoSeLlamaAFleet() throws Exception {
        UUID sucursal = escenario.sucursal();
        UUID busId = UUID.randomUUID();
        escenario.fleetResponde(busId, 503, "Mantenimiento");

        for (int i = 0; i < 5; i++) {
            registrar(sucursal, busId).andExpect(status().isServiceUnavailable());
        }
        assertThat(circuito().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int llamadas = escenario.llamadasAFleet(busId);

        registrar(sucursal, busId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(NO_DISPONIBLE));
        assertThat(escenario.llamadasAFleet(busId)).isEqualTo(llamadas);
    }

    @Test
    void los404NoAbrenElCircuito() throws Exception {
        UUID sucursal = escenario.sucursal();
        UUID busId = UUID.randomUUID();
        escenario.fleetResponde(busId, 404, "No existe ese bus");

        for (int i = 0; i < 10; i++) {
            registrar(sucursal, busId).andExpect(status().isNotFound());
        }
        assertThat(circuito().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(escenario.llamadasAFleet(busId)).isEqualTo(10);
    }

    private ResultActions registrar(UUID sucursal, UUID busId) throws Exception {
        return mvc.perform(post("/api/gastos-taller").with(Escenario.como(Escenario.adminDe(sucursal)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(GastosTallerIT.unaLinea(busId, escenario.categoria(), null)));
    }

    private CircuitBreaker circuito() {
        return circuitos.circuitBreaker("fleet");
    }
}
