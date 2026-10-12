package gt.cunoc.sa.finance.api.reporte;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.finance.api.reporte.dto.ReporteDepreciacionResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteGananciasResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteGastosResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteIngresosResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteTallerPorBusResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteTallerPorCategoriaResponse;
import gt.cunoc.sa.finance.application.Periodo;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Todos con desde y hasta opcionales (inclusivos) sobre la fecha de negocio; 400 si desde > hasta. */
@RestController
@RequestMapping("/api/finanzas/reportes")
@Tag(name = "Reportes financieros", description = "SYS_ADMIN (todas las sucursales o una) y BRANCH_ADMIN (la suya). "
        + "Sin intervalo, todos los registros")
public class ReporteController {

    private final ReporteService servicio;

    public ReporteController(ReporteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/ingresos")
    @Operation(summary = "Ingresos por sucursal, por fecha de pago: cada ingreso, subtotales y total global",
            description = "Boletos en la sucursal de origen de la ruta y alquileres en la que los atiende. "
                    + "Los reversados (venta anulada, alquiler cancelado) no cuentan")
    public ReporteIngresosResponse ingresos(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.ingresos(new Periodo(desde, hasta), sucursalId, actual);
    }

    @GetMapping("/gastos")
    @Operation(summary = "Gastos por sucursal: combustible, depreciación y salarios de cada viaje, y mano de obra "
            + "y repuestos de cada gasto de taller", description = "Viajes por fecha de llegada; taller por fecha "
                    + "del servicio")
    public ReporteGastosResponse gastos(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.gastos(new Periodo(desde, hasta), sucursalId, actual);
    }

    @GetMapping("/ganancias")
    @Operation(summary = "Ingresos, costos y ganancia neta por sucursal, en orden alfabético, con totales")
    public ReporteGananciasResponse ganancias(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.ganancias(new Periodo(desde, hasta), sucursalId, actual);
    }

    @GetMapping("/taller-por-categoria")
    @Operation(summary = "Gastos de taller por categoría: cada línea, total por categoría y gran total",
            description = "Filtros opcionales por sucursal y por bus")
    public ReporteTallerPorCategoriaResponse tallerPorCategoria(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @RequestParam(required = false) UUID busId, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.tallerPorCategoria(new Periodo(desde, hasta), sucursalId, busId, actual);
    }

    @GetMapping("/taller-por-bus")
    @Operation(summary = "Gastos de taller por bus, con subtotal por categoría y total del bus",
            description = "Filtro opcional por bus")
    public ReporteTallerPorBusResponse tallerPorBus(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @RequestParam(required = false) UUID busId, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.tallerPorBus(new Periodo(desde, hasta), sucursalId, busId, actual);
    }

    @GetMapping("/depreciacion-por-bus")
    @Operation(summary = "Km recorridos y depreciación acumulada por bus, con la tarifa por km configurada hoy",
            description = "Cada viaje usó la tarifa vigente a su fecha de llegada")
    public ReporteDepreciacionResponse depreciacion(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.depreciacion(new Periodo(desde, hasta), sucursalId, actual);
    }
}
