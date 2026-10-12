package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * E2, lo publica identity con cada versión nueva de una tarifa. El tipo va como texto: también
 * llegan las de alquiler (ALQUILER_KM, ALQUILER_DIA), que son de customer y finance descarta.
 * aggregateId = tipo y aggregateVersion = tarifaId.
 */
public record ConfiguracionActualizada(
        long tarifaId,
        String tipo,
        BigDecimal valor,
        LocalDate vigenteDesde) {
}
