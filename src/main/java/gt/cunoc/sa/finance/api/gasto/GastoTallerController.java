package gt.cunoc.sa.finance.api.gasto;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.finance.api.gasto.dto.GastoTallerRequest;
import gt.cunoc.sa.finance.api.gasto.dto.GastoTallerResponse;
import gt.cunoc.sa.finance.api.gasto.dto.ResumenGastoResponse;
import gt.cunoc.sa.finance.application.Periodo;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/gastos-taller")
@Tag(name = "Gastos de taller", description = "Gastos de taller y repuestos por bus: encabezado y líneas por categoría")
public class GastoTallerController {

    private final GastoTallerService servicio;

    public GastoTallerController(GastoTallerService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @Operation(summary = "Registra un gasto de un bus de su sucursal; los totales los calcula el sistema",
            description = "BRANCH_ADMIN. 404 si el bus no existe, 403 si es de otra sucursal, 422 categoría "
                    + "inexistente o desactivada o fecha futura, 503 si fleet no responde")
    public ResponseEntity<GastoTallerResponse> registrar(@RequestBody @Valid GastoTallerRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        GastoTallerResponse gasto = servicio.registrar(solicitud, actual);
        return ResponseEntity.created(URI.create("/api/gastos-taller/" + gasto.id())).body(gasto);
    }

    @GetMapping
    @Operation(summary = "Lista paginada, del servicio más reciente",
            description = "SYS_ADMIN (todas o una sucursal) y BRANCH_ADMIN (la suya). Filtros por bus y por fecha "
                    + "del servicio")
    public Page<ResumenGastoResponse> listar(@RequestParam(required = false) UUID sucursalId,
            @RequestParam(required = false) UUID busId, @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @PageableDefault(size = 20, sort = { "fechaServicio", "creadoEn" }, direction = Sort.Direction.DESC)
            Pageable pagina, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.listar(sucursalId, busId, new Periodo(desde, hasta), pagina, actual);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle con sus líneas", description = "SYS_ADMIN y el BRANCH_ADMIN de su sucursal")
    public GastoTallerResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.obtener(id, actual);
    }
}
