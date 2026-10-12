package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ganancias por sucursal en orden alfabético: ingresos, costos y ganancia neta, con totales. Aparecen
 * todas las sucursales (o la pedida), también las que no tuvieron movimiento en el período.
 */
public record ReporteGananciasResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        List<Sucursal> sucursales,
        Ingresos ingresos,
        Costos costos,
        BigDecimal ganancia) {

    public record Sucursal(UUID sucursalId, String nombre, Ingresos ingresos, Costos costos, BigDecimal ganancia) {
    }
}
