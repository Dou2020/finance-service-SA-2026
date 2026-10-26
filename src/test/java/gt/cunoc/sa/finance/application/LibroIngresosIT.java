package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.PruebaDeIntegracion;
import gt.cunoc.sa.finance.Publicador;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;

/** Ingresos y sus reversos (E11, E12, E14, E16) publicados como lo harían sales y customer. */
@PruebaDeIntegracion
class LibroIngresosIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    Publicador publicador;
    @Autowired
    Escenario escenario;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registraLaVentaUnaSolaVezEnLaSucursalYLaFechaDePago() {
        UUID sucursal = escenario.sucursal();
        UUID venta = UUID.randomUUID();
        UUID primero = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));
        // sales no la reemite, pero un duplicado con otro eventId tampoco la cuenta dos veces.
        UUID segundo = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(primero) && publicador.procesado(segundo));

        Map<String, Object> ingreso = ingreso("BOLETO", venta.toString());
        assertThat(ingreso).containsEntry("sucursal_id", sucursal).containsEntry("reversado", false)
                .containsEntry("monto", "225.00").containsEntry("fecha", "2026-10-05");
        // La salida va en la hora de Guatemala: 14:00 UTC son las 08:00.
        assertThat((String) ingreso.get("detalle"))
                .isEqualTo("Quetzaltenango → Ciudad de Guatemala · 20/10/2026 08:00 · 3 asientos · en línea");
        assertThat(cuantos("BOLETO", venta.toString())).isEqualTo(1);
    }

    @Test
    void laAnulacionReversaElIngresoSinCambiarElMonto() {
        UUID sucursal = escenario.sucursal();
        UUID venta = UUID.randomUUID();
        UUID vendida = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(vendida));
        UUID anulada = publicador.publicar(TiposEvento.BOLETOS_ANULADOS, venta.toString(), 2, anulacion(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(anulada));

        Map<String, Object> ingreso = ingreso("BOLETO", venta.toString());
        assertThat(ingreso).containsEntry("reversado", true).containsEntry("monto", "225.00");
        assertThat(ingreso.get("reversado_en")).isNotNull();
    }

    @Test
    void siLaAnulacionLlegaPrimeroLaVentaTardiaNoCuenta() {
        UUID sucursal = escenario.sucursal();
        UUID venta = UUID.randomUUID();
        UUID anulada = publicador.publicar(TiposEvento.BOLETOS_ANULADOS, venta.toString(), 2, anulacion(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(anulada));
        UUID vendida = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(vendida));

        assertThat(ingreso("BOLETO", venta.toString())).containsEntry("reversado", true);
        assertThat(cuantos("BOLETO", venta.toString())).isEqualTo(1);
    }

    @Test
    void elAlquilerPagadoEntraYSuCancelacionLoReversa() {
        UUID sucursal = escenario.sucursal();
        UUID alquiler = UUID.randomUUID();
        UUID pagado = publicador.publicar(TiposEvento.ALQUILER_PAGADO, alquiler.toString(), 3, Map.of(
                "alquilerId", alquiler, "sucursalId", sucursal, "nombre", "Ana López", "origen", "Quetzaltenango",
                "destino", "Antigua Guatemala", "fechaSalida", "2026-10-20", "monto", "1800.00",
                "fechaPago", "2026-10-08", "metodoPago", "EFECTIVO"));
        await().atMost(ESPERA).until(() -> publicador.procesado(pagado));
        assertThat(ingreso("ALQUILER", alquiler.toString())).containsEntry("monto", "1800.00")
                .containsEntry("fecha", "2026-10-08").containsEntry("reversado", false)
                .containsEntry("detalle", "Alquiler · Ana López · Quetzaltenango → Antigua Guatemala · sale 20/10/2026");

        UUID cancelado = publicador.publicar(TiposEvento.ALQUILER_CANCELADO, alquiler.toString(), 6,
                cancelacion(alquiler, sucursal, true, "CAJA"));
        await().atMost(ESPERA).until(() -> publicador.procesado(cancelado));
        assertThat(ingreso("ALQUILER", alquiler.toString())).containsEntry("reversado", true)
                .containsEntry("monto", "1800.00");
    }

    @Test
    void cancelarUnAlquilerQueNoSePagoNoTocaElLibro() {
        UUID alquiler = UUID.randomUUID();
        UUID cancelado = publicador.publicar(TiposEvento.ALQUILER_CANCELADO, alquiler.toString(), 2,
                cancelacion(alquiler, escenario.sucursal(), false, "NINGUNO"));
        await().atMost(ESPERA).until(() -> publicador.procesado(cancelado));

        assertThat(cuantos("ALQUILER", alquiler.toString())).isZero();
    }

    /**
     * Sin su sucursal replicada va a la DLQ sin reintentos; cuando llega el E1, reprocesarlo (el
     * mismo sobre, que es lo que hace POST /internal/dlq/reprocesar) lo aplica completo.
     */
    @Test
    void unaVentaDeUnaSucursalSinReplicarEsperaEnLaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID sucursal = UUID.randomUUID();
        UUID venta = UUID.randomUUID();
        UUID evento = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        assertThat(publicador.procesado(evento)).isFalse();

        UUID e1 = publicador.publicar(TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 1,
                Map.of("sucursalId", sucursal, "nombre", Escenario.codigo("Retalhuleu "), "activa", true));
        await().atMost(ESPERA).until(() -> publicador.procesado(e1));
        publicador.publicar(evento, TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, venta(venta, sucursal));
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));

        assertThat(cuantos("BOLETO", venta.toString())).isEqualTo(1);
    }

    @Test
    void unaVentaSinTotalVaALaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID venta = UUID.randomUUID();
        Map<String, Object> incompleta = venta(venta, escenario.sucursal());
        incompleta.remove("total");
        publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta.toString(), 1, incompleta);

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        assertThat(cuantos("BOLETO", venta.toString())).isZero();
    }

    /** sales numera con bigserial: su id y el de trip llegan como texto ("101") y así quedan en el libro. */
    @Test
    void registraYReversaUnaVentaConLosIdsNumericosDeSalesYTrip() {
        UUID sucursal = escenario.sucursal();
        String venta = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000L));
        Map<String, Object> vendida = venta(venta, sucursal);
        vendida.put("viajeId", "101");
        UUID e11 = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, venta, 1, vendida);
        await().atMost(ESPERA).until(() -> publicador.procesado(e11));
        assertThat(ingreso("BOLETO", venta)).containsEntry("reversado", false).containsEntry("monto", "225.00");

        Map<String, Object> anulada = anulacion(venta, sucursal);
        anulada.put("viajeId", "101");
        UUID e12 = publicador.publicar(TiposEvento.BOLETOS_ANULADOS, venta, 2, anulada);
        await().atMost(ESPERA).until(() -> publicador.procesado(e12));
        assertThat(ingreso("BOLETO", venta)).containsEntry("reversado", true).containsEntry("monto", "225.00");
        assertThat(cuantos("BOLETO", venta)).isEqualTo(1);
    }

    /** Un ventaId vacío o de más de 64 caracteres (el largo de referencia_id, V3) va directo a la DLQ. */
    @Test
    void unVentaIdVacioODeMasDe64VaALaDlq() {
        UUID sucursal = escenario.sucursal();
        String largo = "9".repeat(65);
        int antes = publicador.mensajesEnDlq();
        UUID vacia = publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, "", 1, venta("", sucursal));
        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        UUID larga = publicador.publicar(TiposEvento.BOLETOS_ANULADOS, largo, 2, anulacion(largo, sucursal));
        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes + 1);

        assertThat(publicador.procesado(vacia)).isFalse();
        assertThat(publicador.procesado(larga)).isFalse();
        assertThat(cuantos("BOLETO", "")).isZero();
    }

    /**
     * Como lo publica sales: los campos que lee customer más sucursalId. ventaId y viajeId son texto
     * (V3); aquí van UUID, y en las pruebas de ids numéricos, los de sales y trip.
     */
    private static Map<String, Object> venta(Object venta, UUID sucursal) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ventaId", venta);
        v.put("clienteId", UUID.randomUUID());
        v.put("canal", "ONLINE");
        v.put("viajeId", UUID.randomUUID().toString());
        v.put("sucursalId", sucursal);
        v.put("origen", "Quetzaltenango");
        v.put("destino", "Ciudad de Guatemala");
        v.put("salida", "2026-10-20T14:00:00Z");
        v.put("asientos", List.of(4, 5, 6));
        v.put("total", "225.00");
        v.put("fechaPago", "2026-10-05");
        return v;
    }

    private static Map<String, Object> anulacion(Object venta, UUID sucursal) {
        Map<String, Object> v = venta(venta, sucursal);
        v.put("motivo", "Se eliminó el viaje");
        return v;
    }

    private static Map<String, Object> cancelacion(UUID alquiler, UUID sucursal, boolean pagado, String reembolso) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("alquilerId", alquiler);
        c.put("sucursalId", sucursal);
        c.put("nombre", "Ana López");
        c.put("estabaPagado", pagado);
        c.put("monto", "1800.00");
        c.put("fechaPago", pagado ? "2026-10-08" : null);
        c.put("fecha", "2026-10-15");
        c.put("reembolso", reembolso);
        c.put("motivo", "Se eliminó el viaje del alquiler");
        return c;
    }

    /** referencia_id es texto (V3): el id de sales tal cual, o el UUID del alquiler en texto. */
    private Map<String, Object> ingreso(String tipo, String referencia) {
        return jdbc.queryForMap("""
                select sucursal_id, monto::text as monto, fecha::text as fecha, detalle, reversado, reversado_en
                from ingreso where tipo = ? and referencia_id = ?""", tipo, referencia);
    }

    private int cuantos(String tipo, String referencia) {
        return jdbc.queryForObject("select count(*) from ingreso where tipo = ? and referencia_id = ?", Integer.class,
                tipo, referencia);
    }
}
