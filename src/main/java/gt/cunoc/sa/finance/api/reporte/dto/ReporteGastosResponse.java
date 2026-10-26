package gt.cunoc.sa.finance.api.reporte.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gastos monetarios por sucursal, con la misma estructura que el de ingresos: una fila por viaje
 * finalizado (combustible, depreciación y salario) o por gasto de taller (mano de obra y repuestos),
 * subtotales por concepto y sucursal, y totales.
 */
public record ReporteGastosResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        List<Sucursal> sucursales,
        Costos total) {

    public record Sucursal(UUID sucursalId, String nombre, List<Fila> filas, Costos subtotal) {
    }

    /**
     * @param origen       VIAJE o TALLER
     * @param referenciaId el viaje (su id de trip) o el gasto de taller (su UUID), como texto
     */
    public record Fila(LocalDate fecha, String origen, String referenciaId, String placa, String detalle,
            Costos costos) {
    }
}
