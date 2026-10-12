package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.Escenario.Bus;
import gt.cunoc.sa.finance.PruebaDeIntegracion;
import gt.cunoc.sa.finance.Publicador;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;

/**
 * Los 6 reportes sobre un escenario de septiembre de 2026 con dos sucursales propias, cargado como en
 * producción: ingresos y costos por eventos, gastos de taller por la API. La base es compartida, así
 * que las consultas filtran por estas sucursales y las listas globales se revisan solo en el orden
 * relativo de las dos.
 */
@PruebaDeIntegracion
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportesIT {

    private static final String SEPTIEMBRE = "desde=2026-09-01&hasta=2026-09-30";

    @Autowired
    MockMvc mvc;
    @Autowired
    Escenario escenario;
    @Autowired
    Publicador publicador;
    @Autowired
    JdbcTemplate jdbc;

    UUID avila;
    UUID barberena;
    Bus busAvila;
    UUID busDelViaje;
    String frenos;
    String llantas;

    /**
     * Ávila: boletos 225 (otra venta de 100 se anula y una de 50 es de octubre), alquiler 1800, un
     * viaje de 250 km (combustible 850.75, depreciación 375, salario 300) y dos gastos de taller (mano de
     * obra 350 + 100 en Frenos, repuestos 1200 en Llantas). Barberena: boletos 300 y repuestos 80.
     */
    @BeforeAll
    void cargarEscenario() throws Exception {
        String codigo = Escenario.codigo("");
        // "Ávila" va antes que "Barberena" en español; ordenando por bytes iría después.
        avila = escenario.sucursal("Ávila " + codigo);
        barberena = escenario.sucursal("Barberena " + codigo);
        busAvila = escenario.bus(avila);
        Bus busBarberena = escenario.bus(barberena);
        int categoriaFrenos = escenario.categoria();
        int categoriaLlantas = escenario.categoria();
        frenos = jdbc.queryForObject("select nombre from categoria_gasto where id = ?", String.class, categoriaFrenos);
        llantas = jdbc.queryForObject("select nombre from categoria_gasto where id = ?", String.class, categoriaLlantas);
        jdbc.update("insert into tarifa_depreciacion (id, monto_por_km, vigente_desde) values (?, 1.5, '2026-01-01') "
                + "on conflict do nothing", 950_000_001L);

        List<UUID> eventos = new ArrayList<>();
        UUID anulada = UUID.randomUUID();
        eventos.add(vender(UUID.randomUUID(), avila, "225.00", "2026-09-05"));
        eventos.add(vender(anulada, avila, "100.00", "2026-09-06"));
        eventos.add(vender(UUID.randomUUID(), avila, "50.00", "2026-10-02"));
        eventos.add(vender(UUID.randomUUID(), barberena, "300.00", "2026-09-08"));
        UUID alquiler = UUID.randomUUID();
        eventos.add(publicador.publicar(TiposEvento.ALQUILER_PAGADO, alquiler.toString(), 3, Map.of("alquilerId",
                alquiler, "sucursalId", avila, "nombre", "Ana López", "monto", "1800.00", "fechaPago", "2026-09-07")));
        busDelViaje = UUID.randomUUID();
        UUID viaje = UUID.randomUUID();
        Map<String, Object> llegada = new LinkedHashMap<>();
        llegada.put("viajeId", viaje);
        llegada.put("tipo", "REGULAR");
        llegada.put("sucursalId", avila);
        llegada.put("busId", busDelViaje);
        llegada.put("placa", "P777AAA");
        llegada.put("choferId", UUID.randomUUID());
        llegada.put("salarioBase", "300.00");
        llegada.put("kmInicial", 12_000);
        llegada.put("kmFinal", 12_250);
        llegada.put("combustible", "850.75");
        llegada.put("llegada", "2026-09-10T20:00:00Z");
        eventos.add(publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5, llegada));
        await().atMost(Duration.ofSeconds(10)).until(() -> eventos.stream().allMatch(publicador::procesado));
        Map<String, Object> anulacion = venta(anulada, avila, "100.00", "2026-09-06");
        anulacion.put("motivo", "Se eliminó el viaje");
        UUID e12 = publicador.publicar(TiposEvento.BOLETOS_ANULADOS, anulada.toString(), 2, anulacion);
        await().atMost(Duration.ofSeconds(10)).until(() -> publicador.procesado(e12));

        gasto(avila, busAvila, "2026-09-12", """
                {"categoriaId": %d, "descripcion": "Cambio de zapatas", "tipo": "MANO_DE_OBRA", "monto": 350},
                {"categoriaId": %d, "descripcion": "4 llantas", "tipo": "REPUESTO", "monto": 1200}"""
                .formatted(categoriaFrenos, categoriaLlantas));
        gasto(avila, busAvila, "2026-09-20", """
                {"categoriaId": %d, "descripcion": "Ajuste de frenos", "tipo": "MANO_DE_OBRA", "monto": 100}"""
                .formatted(categoriaFrenos));
        gasto(barberena, busBarberena, "2026-09-15", """
                {"categoriaId": %d, "descripcion": "Llanta de repuesto", "tipo": "REPUESTO", "monto": 80}"""
                .formatted(categoriaLlantas));
    }

    @Test
    void ingresosPorSucursalSinLosReversadosNiLosDeFueraDelPeriodo() throws Exception {
        reporte("ingresos", SEPTIEMBRE + "&sucursalId=" + avila, Escenario.sysAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursales.length()").value(1))
                .andExpect(jsonPath("$.sucursales[0].nombre").value(nombre(avila)))
                .andExpect(jsonPath("$.sucursales[0].filas.length()").value(2))
                .andExpect(jsonPath("$.sucursales[0].filas[0].tipo").value("BOLETO"))
                .andExpect(jsonPath("$.sucursales[0].filas[1].tipo").value("ALQUILER"))
                .andExpect(jsonPath("$.sucursales[0].subtotal.boletos").value(225.00))
                .andExpect(jsonPath("$.sucursales[0].subtotal.alquileres").value(1800.00))
                .andExpect(jsonPath("$.total.total").value(2025.00));
        // Sin intervalo entra también la de octubre.
        reporte("ingresos", "sucursalId=" + avila, Escenario.sysAdmin())
                .andExpect(jsonPath("$.total.boletos").value(275.00));
    }

    @Test
    void gastosPorConceptoConUnaFilaPorViajeYPorGasto() throws Exception {
        reporte("gastos", SEPTIEMBRE + "&sucursalId=" + avila, Escenario.sysAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursales[0].filas.length()").value(3))
                .andExpect(jsonPath("$.sucursales[0].filas[0].origen").value("VIAJE"))
                .andExpect(jsonPath("$.sucursales[0].filas[0].detalle").value("Viaje regular · 250 km"))
                .andExpect(jsonPath("$.sucursales[0].filas[1].origen").value("TALLER"))
                .andExpect(jsonPath("$.total.combustible").value(850.75))
                .andExpect(jsonPath("$.total.depreciacion").value(375.00))
                .andExpect(jsonPath("$.total.salarios").value(300.00))
                .andExpect(jsonPath("$.total.manoDeObra").value(450.00))
                .andExpect(jsonPath("$.total.repuestos").value(1200.00))
                .andExpect(jsonPath("$.total.total").value(3175.75));
    }

    @Test
    void gananciasPorSucursalEnOrdenAlfabetico() throws Exception {
        reporte("ganancias", SEPTIEMBRE + "&sucursalId=" + avila, Escenario.sysAdmin())
                .andExpect(jsonPath("$.sucursales.length()").value(1))
                .andExpect(jsonPath("$.sucursales[0].ingresos.total").value(2025.00))
                .andExpect(jsonPath("$.sucursales[0].costos.total").value(3175.75))
                .andExpect(jsonPath("$.sucursales[0].ganancia").value(-1150.75))
                .andExpect(jsonPath("$.ganancia").value(-1150.75));

        String todas = reporte("ganancias", SEPTIEMBRE, Escenario.sysAdmin()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(todas, "$.sucursales[*].sucursalId");
        assertThat(ids).contains(avila.toString(), barberena.toString());
        assertThat(ids.indexOf(avila.toString())).isLessThan(ids.indexOf(barberena.toString()));
        List<Number> deBarberena = JsonPath.read(todas,
                "$.sucursales[?(@.sucursalId == '" + barberena + "')].ganancia");
        assertThat(deBarberena.get(0).doubleValue()).isEqualTo(220.00);
    }

    @Test
    void tallerPorCategoriaConSuDetalleYTotales() throws Exception {
        reporte("taller-por-categoria", SEPTIEMBRE + "&sucursalId=" + avila, Escenario.sysAdmin())
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[?(@.nombre == '" + frenos + "')].total").value(450.00))
                .andExpect(jsonPath("$.categorias[?(@.nombre == '" + frenos + "')].filas.length()").value(2))
                .andExpect(jsonPath("$.categorias[?(@.nombre == '" + llantas + "')].total").value(1200.00))
                .andExpect(jsonPath("$.granTotal").value(1650.00));
        // Por bus: el de Barberena solo tiene la llanta de repuesto.
        reporte("taller-por-categoria", SEPTIEMBRE + "&busId=" + busAvila.id(), Escenario.sysAdmin())
                .andExpect(jsonPath("$.granTotal").value(1650.00));
    }

    @Test
    void tallerPorBusDesglosadoPorCategoria() throws Exception {
        reporte("taller-por-bus", SEPTIEMBRE, Escenario.adminDe(avila))
                .andExpect(jsonPath("$.buses.length()").value(1))
                .andExpect(jsonPath("$.buses[0].placa").value(busAvila.placa()))
                .andExpect(jsonPath("$.buses[0].gastos").value(2))
                .andExpect(jsonPath("$.buses[0].categorias[?(@.nombre == '" + frenos + "')].manoDeObra").value(450.00))
                .andExpect(jsonPath("$.buses[0].categorias[?(@.nombre == '" + llantas + "')].repuestos").value(1200.00))
                .andExpect(jsonPath("$.buses[0].total").value(1650.00));
    }

    @Test
    void depreciacionAcumuladaPorBus() throws Exception {
        reporte("depreciacion-por-bus", SEPTIEMBRE, Escenario.adminDe(avila))
                .andExpect(jsonPath("$.buses.length()").value(1))
                .andExpect(jsonPath("$.buses[0].busId").value(busDelViaje.toString()))
                .andExpect(jsonPath("$.buses[0].placa").value("P777AAA"))
                .andExpect(jsonPath("$.buses[0].viajes").value(1))
                .andExpect(jsonPath("$.buses[0].kmRecorridos").value(250))
                .andExpect(jsonPath("$.buses[0].depreciacion").value(375.00))
                .andExpect(jsonPath("$.tarifaVigente").exists());
    }

    @Test
    void unAdminDeSucursalSiempreVeLaSuya() throws Exception {
        reporte("ingresos", SEPTIEMBRE + "&sucursalId=" + barberena, Escenario.adminDe(avila))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursalId").value(avila.toString()))
                .andExpect(jsonPath("$.sucursales.length()").value(1))
                .andExpect(jsonPath("$.sucursales[0].sucursalId").value(avila.toString()));
    }

    @Test
    void elCajeroNoVeReportesYSinEncabezadosDa401() throws Exception {
        reporte("ganancias", SEPTIEMBRE, Escenario.cajeroDe(avila)).andExpect(status().isForbidden());
        mvc.perform(get("/api/finanzas/reportes/ganancias")).andExpect(status().isUnauthorized());
    }

    @Test
    void unIntervaloAlRevesDa400() throws Exception {
        reporte("gastos", "desde=2026-10-31&hasta=2026-10-01", Escenario.sysAdmin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha inicial no puede ser posterior a la final"));
    }

    private ResultActions reporte(String nombre, String parametros, UsuarioActual actual) throws Exception {
        return mvc.perform(get("/api/finanzas/reportes/" + nombre + "?" + parametros).with(Escenario.como(actual)));
    }

    private String nombre(UUID sucursal) {
        return jdbc.queryForObject("select nombre from sucursal_replica where id = ?", String.class, sucursal);
    }

    private UUID vender(UUID venta, UUID sucursal, String total, String fechaPago) {
        return publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1,
                venta(venta, sucursal, total, fechaPago));
    }

    private static Map<String, Object> venta(UUID venta, UUID sucursal, String total, String fechaPago) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ventaId", venta);
        v.put("sucursalId", sucursal);
        v.put("canal", "PRESENCIAL");
        v.put("viajeId", UUID.randomUUID());
        v.put("origen", "Quetzaltenango");
        v.put("destino", "San Marcos");
        v.put("salida", "2026-10-20T14:00:00Z");
        v.put("asientos", List.of(1));
        v.put("total", total);
        v.put("fechaPago", fechaPago);
        return v;
    }

    private void gasto(UUID sucursal, Bus bus, String fecha, String lineas) throws Exception {
        mvc.perform(post("/api/gastos-taller").with(Escenario.como(Escenario.adminDe(sucursal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\": \"%s\", \"fechaServicio\": \"%s\", \"lineas\": [%s]}"
                                .formatted(bus.id(), fecha, lineas)))
                .andExpect(status().isCreated());
    }
}
