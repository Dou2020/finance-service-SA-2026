package gt.cunoc.sa.finance.api.categoria;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.finance.api.categoria.dto.CategoriaRequest;
import gt.cunoc.sa.finance.api.categoria.dto.CategoriaResponse;
import gt.cunoc.sa.finance.api.categoria.dto.EstadoCategoriaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categorias-gasto")
@Tag(name = "Categorías de gasto", description = "Catálogo de categorías para las líneas de los gastos de taller")
public class CategoriaController {

    private final CategoriaService servicio;

    public CategoriaController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Lista ordenada por nombre; filtro opcional por estado",
            description = "SYS_ADMIN y BRANCH_ADMIN")
    public List<CategoriaResponse> listar(@RequestParam(required = false) Boolean activa) {
        return servicio.listar(activa);
    }

    @PostMapping
    @Operation(summary = "Agrega una categoría activa", description = "SYS_ADMIN. 409 si el nombre ya existe, "
            + "sin distinguir mayúsculas")
    public ResponseEntity<CategoriaResponse> crear(@RequestBody @Valid CategoriaRequest solicitud) {
        CategoriaResponse creada = servicio.crear(solicitud.nombre());
        return ResponseEntity.created(URI.create("/api/categorias-gasto/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renombra la categoría", description = "SYS_ADMIN. 409 si el nombre ya existe")
    public CategoriaResponse renombrar(@PathVariable int id, @RequestBody @Valid CategoriaRequest solicitud) {
        return servicio.renombrar(id, solicitud.nombre());
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activa o desactiva la categoría",
            description = "SYS_ADMIN. Desactivada no se ofrece para gastos nuevos; los registrados no cambian")
    public CategoriaResponse cambiarEstado(@PathVariable int id,
            @RequestBody @Valid EstadoCategoriaRequest solicitud) {
        return servicio.cambiarEstado(id, solicitud.activa());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina una categoría sin gastos",
            description = "SYS_ADMIN. 409 si tiene gastos registrados: entonces solo se puede desactivar")
    public ResponseEntity<Void> eliminar(@PathVariable int id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
