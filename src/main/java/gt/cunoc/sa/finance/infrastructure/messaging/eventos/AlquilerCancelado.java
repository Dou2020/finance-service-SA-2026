package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E16, lo publica customer al cancelar un alquiler. Solo hay ingreso que reversar si estaba pagado,
 * en cartera o en efectivo (el reembolso en caja también lo saca de los ingresos).
 * aggregateId = alquilerId.
 *
 * @param estabaPagado Boolean y no boolean: un payload sin el campo es inválido, no "sin pagar"
 * @param fechaPago    la del cobro original; null si no estaba pagado
 */
public record AlquilerCancelado(
        UUID alquilerId,
        UUID sucursalId,
        String nombre,
        Boolean estabaPagado,
        BigDecimal monto,
        LocalDate fechaPago,
        String motivo) {
}
