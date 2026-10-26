package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.TipoLinea;

/**
 * Gastos de taller por categoría: el detalle de cada línea y un total por categoría, con un gran
 * total. Dice en qué tipo de gasto se concentra el mantenimiento de la flota.
 */
public record ReporteTallerPorCategoriaResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        String busId,
        List<Categoria> categorias,
        BigDecimal granTotal) {

    public record Categoria(int categoriaId, String nombre, List<Fila> filas, BigDecimal total) {
    }

    public record Fila(UUID gastoId, String placa, String sucursal, LocalDate fecha, String descripcion,
            TipoLinea tipo, BigDecimal monto) {
    }
}
