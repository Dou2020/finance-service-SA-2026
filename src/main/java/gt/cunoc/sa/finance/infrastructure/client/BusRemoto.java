package gt.cunoc.sa.finance.infrastructure.client;

import java.util.UUID;

/**
 * Lo que finance lee de GET /internal/buses/{id} en fleet (S6). fleet todavía no existe: es el
 * contrato mínimo que tiene que cumplir (API.md, "Llama a").
 *
 * @param estado DISPONIBLE, EN_TALLER, EN_RUTA o INACTIVO; un gasto se registra en cualquiera
 */
public record BusRemoto(UUID id, String placa, UUID sucursalId, String estado) {
}
