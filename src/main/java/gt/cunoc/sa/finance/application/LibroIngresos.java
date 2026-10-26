package gt.cunoc.sa.finance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.api.reporte.IngresoRepository;
import gt.cunoc.sa.finance.api.reporte.SucursalReplicaRepository;
import gt.cunoc.sa.finance.domain.Calendario;
import gt.cunoc.sa.finance.domain.TipoIngreso;
import gt.cunoc.sa.finance.infrastructure.messaging.Inbox;
import gt.cunoc.sa.finance.infrastructure.messaging.Payloads;
import gt.cunoc.sa.finance.infrastructure.messaging.Sobre;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.AlquilerCancelado;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.AlquilerPagado;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.BoletosAnulados;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.BoletosVendidos;

/**
 * El libro de ingresos (README, supuestos 6 y 7): boletos por la sucursal de origen de la ruta y
 * alquileres por la sucursal que los atiende, siempre en la fecha de pago. La recarga de cartera no
 * es ingreso. Las compensaciones no borran: marcan el ingreso reversado, y si el reverso llega primero
 * deja una lápida que el ingreso tardío no puede pisar.
 */
@Service
public class LibroIngresos {

    private static final Logger log = LoggerFactory.getLogger(LibroIngresos.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LARGO_DETALLE = 200;

    private final Inbox inbox;
    private final IngresoRepository ingresos;
    private final SucursalReplicaRepository sucursales;
    private final ObjectMapper json;

    public LibroIngresos(Inbox inbox, IngresoRepository ingresos, SucursalReplicaRepository sucursales,
            ObjectMapper json) {
        this.inbox = inbox;
        this.ingresos = ingresos;
        this.sucursales = sucursales;
        this.json = json;
    }

    /** E11: la venta confirmada, en línea o en mostrador. */
    @Transactional
    public void boletosVendidos(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        BoletosVendidos v = Payloads.leer(json, sobre, BoletosVendidos.class);
        Venta venta = new Venta(v.ventaId(), v.sucursalId(), v.canal(), v.origen(), v.destino(), v.salida(),
                v.asientos(), v.total(), v.fechaPago());
        exigir(sobre, venta);
        if (ingresos.registrar(TipoIngreso.BOLETO.name(), venta.ventaId(), venta.sucursalId(), venta.total(),
                venta.fechaPago(), venta.detalle()) == 0) {
            log.info("La venta {} ya estaba en el libro (o anulada); se descarta", venta.ventaId());
        }
    }

    /**
     * E12: la venta se anuló (se eliminó el viaje, o se inició mientras se cobraba). Si sales la anuló
     * antes de confirmarla nunca hubo E11: la lápida queda igual y no cuenta en ningún reporte.
     */
    @Transactional
    public void boletosAnulados(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        BoletosAnulados v = Payloads.leer(json, sobre, BoletosAnulados.class);
        Venta venta = new Venta(v.ventaId(), v.sucursalId(), v.canal(), v.origen(), v.destino(), v.salida(),
                v.asientos(), v.total(), v.fechaPago());
        exigir(sobre, venta);
        ingresos.reversar(TipoIngreso.BOLETO.name(), venta.ventaId(), venta.sucursalId(), venta.total(),
                venta.fechaPago(), venta.detalle());
    }

    /** E14: el alquiler quedó pagado, con cartera o en efectivo. */
    @Transactional
    public void alquilerPagado(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        AlquilerPagado a = Payloads.leer(json, sobre, AlquilerPagado.class);
        Payloads.exigir(a.alquilerId() != null && a.sucursalId() != null && a.monto() != null
                && a.fechaPago() != null, sobre, "alquilerId, sucursalId, monto o fechaPago");
        exigirSucursal(a.sucursalId());
        String trayecto = a.origen() == null || a.destino() == null ? null : a.origen() + " → " + a.destino();
        String salida = a.fechaSalida() == null ? null : "sale " + a.fechaSalida().format(FECHA);
        // referencia_id es texto (V3) por las ventas de sales; el alquiler va con su UUID canónico.
        if (ingresos.registrar(TipoIngreso.ALQUILER.name(), a.alquilerId().toString(), a.sucursalId(), a.monto(),
                a.fechaPago(), detalle("Alquiler", a.nombre(), trayecto, salida)) == 0) {
            log.info("El alquiler {} ya estaba en el libro (o cancelado); se descarta", a.alquilerId());
        }
    }

    /**
     * E16: el alquiler se canceló. Solo hay ingreso que reversar si estaba pagado; el reembolso puede
     * ser a la cartera o en caja, y en los dos casos sale de los ingresos.
     */
    @Transactional
    public void alquilerCancelado(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        AlquilerCancelado a = Payloads.leer(json, sobre, AlquilerCancelado.class);
        Payloads.exigir(a.alquilerId() != null && a.estabaPagado() != null, sobre, "alquilerId o estabaPagado");
        if (!a.estabaPagado()) {
            return;
        }
        Payloads.exigir(a.sucursalId() != null && a.monto() != null && a.fechaPago() != null, sobre,
                "sucursalId, monto o fechaPago de un alquiler pagado");
        exigirSucursal(a.sucursalId());
        ingresos.reversar(TipoIngreso.ALQUILER.name(), a.alquilerId().toString(), a.sucursalId(), a.monto(),
                a.fechaPago(), detalle("Alquiler", a.nombre()));
    }

    private void exigir(Sobre sobre, Venta venta) {
        Payloads.exigirId(venta.ventaId(), sobre, "ventaId");
        Payloads.exigir(venta.completa(), sobre, "datos de la venta");
        exigirSucursal(venta.sucursalId());
    }

    private void exigirSucursal(UUID sucursalId) {
        if (!sucursales.existsById(sucursalId)) {
            throw new ReplicaPendienteException("La sucursal " + sucursalId + " todavía no está replicada en finance");
        }
    }

    /** Las partes que hay, separadas por " · ", hasta lo que cabe en la columna. */
    private static String detalle(String... partes) {
        String texto = String.join(" · ", Arrays.stream(partes).filter(p -> p != null && !p.isBlank())
                .toList());
        return texto.length() <= LARGO_DETALLE ? texto : texto.substring(0, LARGO_DETALLE - 1) + "…";
    }

    /** Lo que E11 y E12 traen de la venta; ventaId es el id de sales como texto. */
    private record Venta(String ventaId, UUID sucursalId, String canal, String origen, String destino,
            Instant salida, List<Integer> asientos, BigDecimal total, LocalDate fechaPago) {

        /** Sin ventaId, que se valida aparte (Payloads.exigirId). */
        boolean completa() {
            return sucursalId != null && origen != null && destino != null && salida != null
                    && asientos != null && !asientos.isEmpty() && total != null && fechaPago != null;
        }

        /** "Quetzaltenango → Ciudad de Guatemala · 20/10/2026 08:00 · 3 asientos · en línea" */
        String detalle() {
            String canalTexto = "ONLINE".equals(canal) ? "en línea"
                    : "PRESENCIAL".equals(canal) ? "en mostrador" : null;
            return LibroIngresos.detalle(origen + " → " + destino,
                    salida.atZone(Calendario.ZONA).format(FECHA_HORA),
                    asientos.size() + (asientos.size() == 1 ? " asiento" : " asientos"), canalTexto);
        }
    }
}
