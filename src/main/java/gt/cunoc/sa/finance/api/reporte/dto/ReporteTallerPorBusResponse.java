package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gastos de taller por bus, desglosados por categoría: un bus con gastos recurrentes en una categoría
 * (motor, por ejemplo) es candidato a desactivarse (enunciado, "Reportes").
 */
public record ReporteTallerPorBusResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        String busId,
        List<Bus> buses,
        BigDecimal granTotal) {

    /**
     * @param busId  el id de fleet como texto
     * @param gastos cuántos gastos de taller tuvo en el período
     */
    public record Bus(String busId, String placa, String sucursal, long gastos, List<Categoria> categorias,
            BigDecimal manoDeObra, BigDecimal repuestos, BigDecimal total) {
    }

    public record Categoria(String nombre, BigDecimal manoDeObra, BigDecimal repuestos, BigDecimal subtotal) {
    }
}
