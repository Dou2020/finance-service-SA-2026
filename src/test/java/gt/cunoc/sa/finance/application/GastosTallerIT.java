package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.Escenario.Bus;
import gt.cunoc.sa.finance.PruebaDeIntegracion;
import gt.cunoc.sa.finance.domain.Calendario;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;

/** Gastos de taller con fleet (S6) simulado en WireMock. */
@PruebaDeIntegracion
class GastosTallerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    Escenario escenario;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registraElGastoConSusTotalesYLaPlacaDeFleet() throws Exception {
        UUID sucursal = escenario.sucursal();
        Bus bus = escenario.bus(sucursal);
        int llantas = escenario.categoria();
        int aceite = escenario.categoria();

        String id = JsonPath.read(registrar(Escenario.adminDe(sucursal), """
                {"busId": "%s", "fechaServicio": "2026-09-30", "lineas": [
                  {"categoriaId": %d, "descripcion": "Cambio de 2 llantas", "tipo": "REPUESTO", "monto": 1200.50},
                  {"categoriaId": %d, "descripcion": "Montaje", "tipo": "MANO_DE_OBRA", "monto": 150},
                  {"categoriaId": %d, "descripcion": "Aceite 15W40", "tipo": "REPUESTO", "monto": 99.99},
                  {"categoriaId": %d, "descripcion": "Cambio de aceite", "tipo": "MANO_DE_OBRA", "monto": 200}
                ]}""".formatted(bus.id(), llantas, llantas, aceite, aceite))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.placa").value(bus.placa()))
                .andExpect(jsonPath("$.sucursalId").value(sucursal.toString()))
                .andExpect(jsonPath("$.fechaServicio").value("2026-09-30"))
                .andExpect(jsonPath("$.totalManoObra").value(350.00))
                .andExpect(jsonPath("$.totalRepuestos").value(1300.49))
                .andExpect(jsonPath("$.total").value(1650.49))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/gastos-taller/" + id).with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineas.length()").value(4))
                .andExpect(jsonPath("$.lineas[0].descripcion").value("Cambio de 2 llantas"))
                .andExpect(jsonPath("$.lineas[0].categoria").exists());
    }

    @Test
    void sinFechaElServicioEsDeHoyEnGuatemala() throws Exception {
        UUID sucursal = escenario.sucursal();
        registrar(Escenario.adminDe(sucursal), unaLinea(escenario.bus(sucursal).id(), escenario.categoria(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaServicio").value(Calendario.hoy().toString()));
    }

    @Test
    void unaFechaFuturaDa422() throws Exception {
        UUID sucursal = escenario.sucursal();
        registrar(Escenario.adminDe(sucursal), unaLinea(escenario.bus(sucursal).id(), escenario.categoria(),
                Calendario.hoy().plusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La fecha del servicio no puede ser posterior a hoy"));
    }

    @Test
    void unBusDeOtraSucursalDa403YNoGuardaNada() throws Exception {
        UUID suya = escenario.sucursal();
        Bus ajeno = escenario.bus(escenario.sucursal());

        registrar(Escenario.adminDe(suya), unaLinea(ajeno.id(), escenario.categoria(), null))
                .andExpect(status().isForbidden());
        assertThat(gastosDe(ajeno.id())).isZero();
    }

    @Test
    void unBusQueFleetNoConoceDevuelveSu404() throws Exception {
        UUID busId = UUID.randomUUID();
        escenario.fleetResponde(busId, 404, "No existe ese bus");

        registrar(Escenario.adminDe(escenario.sucursal()), unaLinea(busId, escenario.categoria(), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe ese bus"));
        // Un 4xx es la respuesta de un servicio sano: no se reintenta.
        assertThat(escenario.llamadasAFleet(busId)).isEqualTo(1);
    }

    @Test
    void unaCategoriaDesactivadaOInexistenteDa422() throws Exception {
        UUID sucursal = escenario.sucursal();
        Bus bus = escenario.bus(sucursal);
        int categoria = escenario.categoria();
        jdbc.update("update categoria_gasto set activa = false where id = ?", categoria);
        String nombre = jdbc.queryForObject("select nombre from categoria_gasto where id = ?", String.class, categoria);

        registrar(Escenario.adminDe(sucursal), unaLinea(bus.id(), categoria, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La categoría " + nombre + " está desactivada"));
        registrar(Escenario.adminDe(sucursal), unaLinea(bus.id(), 999_999, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("No existe la categoría 999999"));
        assertThat(gastosDe(bus.id())).isZero();
    }

    @Test
    void unaSucursalQueFinanceTodaviaNoReplicaDa409() throws Exception {
        UUID sinReplica = UUID.randomUUID();
        registrar(Escenario.adminDe(sinReplica), unaLinea(escenario.bus(sinReplica).id(), escenario.categoria(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Tu sucursal todavía no está disponible en finanzas. Intenta de nuevo en unos minutos"));
    }

    @Test
    void validaLasLineas() throws Exception {
        UUID sucursal = escenario.sucursal();
        UUID busId = escenario.bus(sucursal).id();

        registrar(Escenario.adminDe(sucursal), "{\"busId\": \"%s\", \"lineas\": []}".formatted(busId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.lineas").value("debe tener al menos una línea"));
        registrar(Escenario.adminDe(sucursal), """
                {"busId": "%s", "lineas": [{"categoriaId": 1, "descripcion": "x", "tipo": "REPUESTO", "monto": 0}]}"""
                .formatted(busId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['lineas[0].monto']").value("debe ser mayor que cero"));
        // Sin llamar a fleet: la validación va antes.
        assertThat(escenario.llamadasAFleet(busId)).isZero();
    }

    @Test
    void soloElAdminDeSucursalRegistra() throws Exception {
        UUID sucursal = escenario.sucursal();
        String cuerpo = unaLinea(escenario.bus(sucursal).id(), escenario.categoria(), null);

        registrar(Escenario.cajeroDe(sucursal), cuerpo).andExpect(status().isForbidden());
        registrar(Escenario.sysAdmin(), cuerpo).andExpect(status().isForbidden());
        mvc.perform(post("/api/gastos-taller").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unAdminSoloVeLosGastosDeSuSucursal() throws Exception {
        UUID a = escenario.sucursal();
        UUID b = escenario.sucursal();
        Bus busA = escenario.bus(a);
        Bus busB = escenario.bus(b);
        int categoria = escenario.categoria();
        String gastoA = registrarId(a, busA, categoria, "2026-09-01");
        String otroA = registrarId(a, busA, categoria, "2026-09-20");
        String gastoB = registrarId(b, busB, categoria, "2026-09-10");

        mvc.perform(get("/api/gastos-taller").param("sucursalId", b.toString())   // lo ignora: no es SYS_ADMIN
                        .with(Escenario.como(Escenario.adminDe(a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(otroA, gastoA)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/gastos-taller").param("sucursalId", b.toString())
                        .with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(gastoB)));
        mvc.perform(get("/api/gastos-taller").param("desde", "2026-09-15")
                        .with(Escenario.como(Escenario.adminDe(a))))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(otroA)));
        mvc.perform(get("/api/gastos-taller/" + gastoB).with(Escenario.como(Escenario.adminDe(a))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unIntervaloAlRevesDa400() throws Exception {
        mvc.perform(get("/api/gastos-taller").param("desde", "2026-10-01").param("hasta", "2026-09-01")
                        .with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha inicial no puede ser posterior a la final"));
    }

    private String registrarId(UUID sucursal, Bus bus, int categoria, String fecha) throws Exception {
        return JsonPath.read(registrar(Escenario.adminDe(sucursal), unaLinea(bus.id(), categoria, fecha))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions registrar(UsuarioActual actual, String cuerpo) throws Exception {
        return mvc.perform(post("/api/gastos-taller").with(Escenario.como(actual))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    static String unaLinea(UUID busId, int categoria, String fecha) {
        String fechaJson = fecha == null ? "null" : "\"" + fecha + "\"";
        return """
                {"busId": "%s", "fechaServicio": %s, "lineas": [
                  {"categoriaId": %d, "descripcion": "Frenos", "tipo": "MANO_DE_OBRA", "monto": 250}
                ]}""".formatted(busId, fechaJson, categoria);
    }

    private int gastosDe(UUID busId) {
        return jdbc.queryForObject("select count(*) from gasto_taller where bus_id = ?", Integer.class, busId);
    }
}
