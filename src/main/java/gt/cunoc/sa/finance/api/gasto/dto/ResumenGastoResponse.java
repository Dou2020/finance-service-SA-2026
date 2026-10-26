package gt.cunoc.sa.finance.api.gasto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.GastoTallerEntity;

/** Una fila de la lista de gastos: el encabezado, sin las líneas (están en el detalle). */
public record ResumenGastoResponse(
        UUID id,
        String busId,
        String placa,
        UUID sucursalId,
        LocalDate fechaServicio,
        BigDecimal totalManoObra,
        BigDecimal totalRepuestos,
        BigDecimal total,
        Instant creadoEn) {

    public static ResumenGastoResponse de(GastoTallerEntity g) {
        return new ResumenGastoResponse(g.getId(), g.getBusId(), g.getPlacaBus(), g.getSucursalId(),
                g.getFechaServicio(), g.getTotalManoObra(), g.getTotalRepuestos(), g.getTotal(), g.getCreadoEn());
    }
}
