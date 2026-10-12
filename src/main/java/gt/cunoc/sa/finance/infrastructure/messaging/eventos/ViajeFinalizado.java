package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.TipoViaje;

/**
 * E9, lo publica trip al registrar la llegada. trip todavía no existe: este record es el contrato
 * mínimo que tiene que cumplir (API.md, "Consume"). aggregateId = viajeId.
 *
 * @param sucursalId  la de origen de la ruta (REGULAR) o la que atiende el alquiler (ALQUILER)
 * @param salarioBase el snapshot que trip guardó al asignar el chofer; finance aplica el 15 %
 * @param combustible el gasto total que se registró en la llegada
 * @param llegada     la hora real de llegada que eligió el usuario; su fecha en Guatemala es la del costo
 */
public record ViajeFinalizado(
        UUID viajeId,
        TipoViaje tipo,
        UUID alquilerId,
        UUID sucursalId,
        UUID busId,
        String placa,
        UUID choferId,
        BigDecimal salarioBase,
        Integer kmInicial,
        Integer kmFinal,
        BigDecimal combustible,
        Instant llegada) {
}
