package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.TipoIngreso;

/**
 * Ingresos monetarios por sucursal (enunciado, "Reportes"): por sucursal, en orden alfabético, cada
 * ingreso con su subtotal, y el total global. Los reversados no cuentan.
 *
 * @param desde      null si no se filtró: entonces cuenta desde el primer registro
 * @param sucursalId null si un SYS_ADMIN pidió todas
 */
public record ReporteIngresosResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        List<Sucursal> sucursales,
        Ingresos total) {

    public record Sucursal(UUID sucursalId, String nombre, List<Fila> filas, Ingresos subtotal) {
    }

    /** @param referenciaId la venta de sales (BOLETO) o el alquiler de customer (ALQUILER), como texto */
    public record Fila(LocalDate fecha, TipoIngreso tipo, String referenciaId, String detalle, BigDecimal monto) {
    }
}
