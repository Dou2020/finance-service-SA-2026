package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * E11, lo publica sales al confirmar una venta, en línea o en mostrador. Los mismos campos que lee
 * customer más sucursalId; sales todavía no existe, así que es el contrato que tiene que cumplir
 * (API.md, "Consume"). aggregateId = ventaId.
 *
 * @param sucursalId la de origen de la ruta: ahí se reconoce el ingreso (README, supuesto 7)
 * @param canal      ONLINE o PRESENCIAL; solo se usa en el detalle
 * @param salida     fecha y hora de salida del viaje
 */
public record BoletosVendidos(
        UUID ventaId,
        UUID sucursalId,
        String canal,
        UUID viajeId,
        String origen,
        String destino,
        Instant salida,
        List<Integer> asientos,
        BigDecimal total,
        LocalDate fechaPago) {
}
