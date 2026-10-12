package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Depreciación por bus: km recorridos y depreciación acumulada de sus viajes finalizados, con la tarifa
 * por km configurada hoy. Cada viaje usó la vigente a su fecha de llegada; si cambió, la acumulada no
 * es km × la de hoy.
 *
 * @param tarifaVigente      la que está configurada hoy; null si todavía no hay ninguna
 * @param tarifaVigenteDesde desde cuándo vale
 */
public record ReporteDepreciacionResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        BigDecimal tarifaVigente,
        LocalDate tarifaVigenteDesde,
        List<Bus> buses,
        long viajes,
        long kmRecorridos,
        BigDecimal depreciacion) {

    public record Bus(UUID busId, String placa, String sucursal, long viajes, long kmRecorridos,
            BigDecimal depreciacion) {
    }
}
