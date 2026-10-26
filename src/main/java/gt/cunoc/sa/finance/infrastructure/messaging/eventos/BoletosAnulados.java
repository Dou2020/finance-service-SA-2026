package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * E12, lo publica sales al anular una venta. Trae la venta completa, como E11: si llega antes que
 * él (o sin él, porque la venta se anuló antes de confirmarse), finance deja el ingreso ya reversado
 * con estos datos y el E11 tardío no lo cuenta. ventaId y viajeId van como texto, igual que en E11.
 */
public record BoletosAnulados(
        String ventaId,
        UUID sucursalId,
        String canal,
        String viajeId,
        String origen,
        String destino,
        Instant salida,
        List<Integer> asientos,
        BigDecimal total,
        LocalDate fechaPago,
        String motivo) {
}
