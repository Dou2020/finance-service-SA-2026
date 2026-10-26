package gt.cunoc.sa.finance.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal del SecurityContext, armado con los X-User-* que escribe el gateway.
 * sucursalId solo viene para BRANCH_ADMIN y CASHIER; choferId solo para DRIVER, y es texto: es el
 * id del chofer en fleet, que numera con bigserial (identity lo guarda como texto desde la fase 0
 * del plan de eventos, 2026-10-25).
 */
public record UsuarioActual(UUID usuarioId, UUID sucursalId, String choferId, Set<String> roles) {

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }
}
