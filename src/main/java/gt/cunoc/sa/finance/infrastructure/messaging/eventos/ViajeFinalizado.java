package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.TipoViaje;

/**
 * E9, lo publica trip al registrar la llegada. Es el contrato mínimo que trip tiene que cumplir
 * (API.md, "Eventos que consume"). aggregateId = viajeId.
 *
 * viajeId, busId y choferId son ids de trip y fleet, que numeran con bigserial: llegan como texto
 * ("101"), o como número JSON, que Jackson lee como su texto. Payloads.exigirId los valida.
 *
 * @param sucursalId  la de origen de la ruta (REGULAR) o la que atiende el alquiler (ALQUILER)
 * @param salarioBase el snapshot que trip guardó al asignar el chofer; finance aplica el 15 %
 * @param combustible el gasto total que se registró en la llegada
 * @param llegada     la hora real de llegada que eligió el usuario; su fecha en Guatemala es la del costo
 */
public record ViajeFinalizado(
        String viajeId,
        TipoViaje tipo,
        UUID alquilerId,
        UUID sucursalId,
        String busId,
        String placa,
        String choferId,
        BigDecimal salarioBase,
        Integer kmInicial,
        Integer kmFinal,
        BigDecimal combustible,
        Instant llegada) {
}
