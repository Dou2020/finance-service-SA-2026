package gt.cunoc.sa.finance.api.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.TipoLinea;

/** Una línea de gasto con su encabezado y su categoría: la fila de los dos reportes de taller. */
public record LineaDeTaller(
        UUID gastoId,
        UUID busId,
        String placa,
        UUID sucursalId,
        LocalDate fecha,
        int categoriaId,
        String categoria,
        String descripcion,
        TipoLinea tipo,
        BigDecimal monto) {
}
