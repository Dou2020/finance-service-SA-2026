package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.PruebaDeIntegracion;
import gt.cunoc.sa.finance.Publicador;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;

/**
 * ViajeFinalizado (E9) publicado como lo hará trip. La tabla de tarifas es global y vigente_desde es
 * única: cada prueba usa un año propio, lejos de los de las demás.
 */
@PruebaDeIntegracion
class CostosDeViajeIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final AtomicLong TARIFAS = new AtomicLong(ThreadLocalRandom.current().nextLong(900_000_000L));

    @Autowired
    Publicador publicador;
    @Autowired
    Escenario escenario;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaLaTarifaVigenteALaFechaDeLlegadaEnGuatemala() {
        int anio = anio();
        tarifa(anio + "-01-01", "1.5000");
        tarifa(anio + "-03-11", "9.0000");
        UUID sucursal = escenario.sucursal();
        UUID viaje = UUID.randomUUID();
        // 03:00 UTC del 11 de marzo son las 21:00 del 10 en Guatemala: vale la tarifa del 1 de enero.
        UUID evento = publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5,
                viaje(viaje, sucursal, "REGULAR", anio + "-03-11T03:00:00Z", 10_000, 10_250));
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));

        assertThat(costo(viaje))
                .containsEntry("fecha", anio + "-03-10")
                .containsEntry("km_recorridos", 250)
                .containsEntry("tarifa_aplicada", "1.5000")
                .containsEntry("depreciacion", "375.00")
                .containsEntry("salario_chofer", "300.00")
                .containsEntry("combustible", "850.75")
                .containsEntry("sucursal_id", sucursal);

        // Una tarifa nueva para esa fecha no recalcula lo registrado.
        tarifa(anio + "-02-01", "4.0000");
        assertThat(costo(viaje)).containsEntry("depreciacion", "375.00");
    }

    @Test
    void enUnAlquilerElSalarioLlevaEl15PorCiento() {
        int anio = anio();
        tarifa(anio + "-01-01", "2.0000");
        UUID viaje = UUID.randomUUID();
        UUID evento = publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 4,
                viaje(viaje, escenario.sucursal(), "ALQUILER", anio + "-05-01T18:00:00Z", 500, 600));
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));

        assertThat(costo(viaje)).containsEntry("tipo_viaje", "ALQUILER").containsEntry("salario_chofer", "345.00")
                .containsEntry("depreciacion", "200.00");
    }

    @Test
    void unaReemisionDelMismoViajeNoDuplicaElCosto() {
        int anio = anio();
        tarifa(anio + "-01-01", "1.0000");
        UUID sucursal = escenario.sucursal();
        UUID viaje = UUID.randomUUID();
        Map<String, Object> datos = viaje(viaje, sucursal, "REGULAR", anio + "-07-01T18:00:00Z", 0, 100);
        UUID primero = publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5, datos);
        UUID segundo = publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5, datos);
        await().atMost(ESPERA).until(() -> publicador.procesado(primero) && publicador.procesado(segundo));

        assertThat(jdbc.queryForObject("select count(*) from costo_viaje where viaje_id = ?", Integer.class, viaje))
                .isEqualTo(1);
    }

    /**
     * Sin tarifa vigente a esa fecha va a la DLQ sin reintentos; configurada la tarifa, reprocesarlo
     * (el mismo sobre) lo aplica.
     */
    @Test
    void sinTarifaVigenteEsperaEnLaDlqHastaQueSeConfigure() {
        // Antes de cualquier tarifa de las demás pruebas (años 2100 en adelante).
        int anio = 1900 + ThreadLocalRandom.current().nextInt(90);
        int antes = publicador.mensajesEnDlq();
        UUID viaje = UUID.randomUUID();
        Map<String, Object> datos = viaje(viaje, escenario.sucursal(), "REGULAR", anio + "-08-15T18:00:00Z", 0, 80);
        UUID evento = publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5, datos);

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        assertThat(publicador.procesado(evento)).isFalse();

        tarifa(anio + "-01-01", "0.5000");
        publicador.publicar(evento, TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5, datos);
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
        assertThat(costo(viaje)).containsEntry("depreciacion", "40.00");
    }

    @Test
    void unKmFinalMenorQueElInicialVaDirectoALaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID viaje = UUID.randomUUID();
        publicador.publicar(TiposEvento.VIAJE_FINALIZADO, viaje.toString(), 5,
                viaje(viaje, escenario.sucursal(), "REGULAR", "2026-10-01T18:00:00Z", 1_000, 900));

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        assertThat(jdbc.queryForObject("select count(*) from costo_viaje where viaje_id = ?", Integer.class, viaje))
                .isZero();
    }

    /** Como lo publicará trip (API.md, "Consume"). */
    private static Map<String, Object> viaje(UUID viaje, UUID sucursal, String tipo, String llegada, int kmInicial,
            int kmFinal) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("viajeId", viaje);
        v.put("tipo", tipo);
        v.put("alquilerId", "ALQUILER".equals(tipo) ? UUID.randomUUID() : null);
        v.put("sucursalId", sucursal);
        v.put("busId", UUID.randomUUID());
        v.put("placa", "P456DEF");
        v.put("choferId", UUID.randomUUID());
        v.put("salarioBase", "300.00");
        v.put("kmInicial", kmInicial);
        v.put("kmFinal", kmFinal);
        v.put("combustible", "850.75");
        v.put("llegada", llegada);
        return v;
    }

    /** Un año entre 3000 y 9999 que ninguna otra prueba de esta clase usa. */
    private static int anio() {
        return 3000 + (int) (TARIFAS.incrementAndGet() % 7000);
    }

    /** Directo a la réplica, como si hubiera llegado su E2. */
    private void tarifa(String vigenteDesde, String monto) {
        jdbc.update("insert into tarifa_depreciacion (id, monto_por_km, vigente_desde) values (?, ?::numeric, ?::date)",
                TARIFAS.incrementAndGet(), monto, vigenteDesde);
    }

    private Map<String, Object> costo(UUID viaje) {
        return jdbc.queryForMap("""
                select tipo_viaje, sucursal_id, fecha::text as fecha, km_recorridos, combustible::text as combustible,
                       tarifa_aplicada::text as tarifa_aplicada, depreciacion::text as depreciacion,
                       salario_chofer::text as salario_chofer
                from costo_viaje where viaje_id = ?""", viaje);
    }
}
