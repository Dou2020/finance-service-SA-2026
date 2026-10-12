package gt.cunoc.sa.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gt.cunoc.sa.finance.PruebaDeIntegracion;
import gt.cunoc.sa.finance.Publicador;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;

/** SucursalGuardada (E1) y ConfiguracionActualizada (E2) publicados como lo haría identity. */
@PruebaDeIntegracion
class ReplicasIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    Publicador publicador;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void replicaLaSucursalYUnaVersionViejaNoPisaALaNueva() {
        UUID sucursal = UUID.randomUUID();
        UUID v1 = publicador.publicar(TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 1,
                sucursal(sucursal, "Quetzaltenango", true));
        UUID v3 = publicador.publicar(TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 3,
                sucursal(sucursal, "Quetzaltenango Centro", false));
        // La v2 llega después de la v3: se descarta.
        UUID v2 = publicador.publicar(TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 2,
                sucursal(sucursal, "Xela", true));
        await().atMost(ESPERA).until(() -> publicador.procesado(v1) && publicador.procesado(v3)
                && publicador.procesado(v2));

        assertThat(jdbc.queryForMap("select nombre, activa, version from sucursal_replica where id = ?", sucursal))
                .containsEntry("nombre", "Quetzaltenango Centro")
                .containsEntry("activa", false)
                .containsEntry("version", 3L);
    }

    @Test
    void elMismoEventoDosVecesSeAplicaUnaSola() {
        UUID sucursal = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        publicador.publicar(evento, TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 1,
                sucursal(sucursal, "San Marcos", true));
        publicador.publicar(evento, TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 1,
                sucursal(sucursal, "San Marcos", true));
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));

        assertThat(jdbc.queryForObject("select count(*) from processed_event where event_id = ?", Integer.class,
                evento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sucursal_replica where id = ?", Integer.class, sucursal))
                .isEqualTo(1);
    }

    @Test
    void sinNombreVaDirectoALaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID sucursal = UUID.randomUUID();
        publicador.publicar(TiposEvento.SUCURSAL_GUARDADA, sucursal.toString(), 1,
                Map.of("sucursalId", sucursal, "activa", true));

        // Sin reintentos: llega a la DLQ mucho antes de los 15 s que tardarían.
        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() > antes);
        assertThat(jdbc.queryForObject("select count(*) from sucursal_replica where id = ?", Integer.class, sucursal))
                .isZero();
    }

    @Test
    void guardaCadaVersionDeLaTarifaDeDepreciacionUnaSolaVez() {
        long tarifaId = ThreadLocalRandom.current().nextLong(1_000_000, 800_000_000);
        String vigente = (2100 + ThreadLocalRandom.current().nextInt(800)) + "-03-15";
        Map<String, Object> tarifa = Map.of("tarifaId", tarifaId, "tipo", "DEPRECIACION_KM", "valor", "1.2500",
                "vigenteDesde", vigente);

        UUID primero = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "DEPRECIACION_KM", tarifaId, tarifa);
        // La misma versión otra vez, con otro eventId (identity no la reemite, pero un respaldo restaurado sí).
        UUID segundo = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "DEPRECIACION_KM", tarifaId, tarifa);
        await().atMost(ESPERA).until(() -> publicador.procesado(primero) && publicador.procesado(segundo));

        assertThat(jdbc.queryForMap(
                "select monto_por_km::text as monto, vigente_desde::text as desde from tarifa_depreciacion where id = ?",
                tarifaId)).containsEntry("monto", "1.2500").containsEntry("desde", vigente);
        assertThat(jdbc.queryForObject("select count(*) from tarifa_depreciacion where id = ?", Integer.class,
                tarifaId)).isEqualTo(1);
    }

    @Test
    void lasTarifasDeAlquilerSonDeCustomerYSeDescartan() {
        long tarifaId = ThreadLocalRandom.current().nextLong(1_000_000, 800_000_000);
        UUID evento = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "ALQUILER_KM", tarifaId,
                Map.of("tarifaId", tarifaId, "tipo", "ALQUILER_KM", "valor", "7.0000", "vigenteDesde", "2041-01-01"));

        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
        assertThat(jdbc.queryForObject("select count(*) from tarifa_depreciacion where id = ?", Integer.class,
                tarifaId)).isZero();
    }

    private static Map<String, Object> sucursal(UUID id, String nombre, boolean activa) {
        // Como lo publica identity: finance ignora los campos que no usa.
        return Map.of("sucursalId", id, "codigo", "QTZ", "nombre", nombre, "departamento", "Quetzaltenango",
                "municipio", "Quetzaltenango", "latitud", 14.834999, "longitud", -91.518611, "activa", activa);
    }
}
