package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * E1, lo publica identity al crear o cambiar una sucursal. Solo los campos que finance usa para
 * agrupar los reportes. aggregateId = sucursalId y aggregateVersion = sucursal.version.
 *
 * @param activa Boolean y no boolean: un payload sin el campo es inválido, no "inactiva"
 */
public record SucursalGuardada(
        UUID sucursalId,
        String nombre,
        Boolean activa) {
}
