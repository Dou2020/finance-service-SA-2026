package gt.cunoc.sa.finance.infrastructure.client;

import java.util.UUID;

/**
 * Lo que finance lee de GET /internal/buses/{id} en fleet (S6): el contrato mínimo que fleet tiene
 * que cumplir (API.md, "Llama a").
 *
 * @param id         el de fleet, como texto: fleet numera con bigserial y lo manda como número JSON,
 *                   que Jackson lee como su texto ("7"). Es el que se guarda en el gasto
 * @param sucursalId la de identity (UUID)
 * @param estado     DISPONIBLE, EN_TALLER, EN_RUTA o INACTIVO; un gasto se registra en cualquiera
 */
public record BusRemoto(String id, String placa, UUID sucursalId, String estado) {
}
