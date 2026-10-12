package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E14, lo publica customer al cobrar un alquiler (cartera o efectivo). Solo los campos que finance
 * usa; el resto del payload se ignora. aggregateId = alquilerId.
 *
 * @param sucursalId la que atiende el alquiler: ahí se reconoce el ingreso (README, supuesto 7)
 * @param monto      el precio final confirmado
 */
public record AlquilerPagado(
        UUID alquilerId,
        UUID sucursalId,
        String nombre,
        String origen,
        String destino,
        LocalDate fechaSalida,
        BigDecimal monto,
        LocalDate fechaPago) {
}
