package gt.cunoc.sa.finance.api.gasto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.finance.domain.GastoTallerEntity;
import gt.cunoc.sa.finance.domain.LineaGastoEntity;
import gt.cunoc.sa.finance.domain.TipoLinea;

/** El gasto con sus líneas. */
public record GastoTallerResponse(
        UUID id,
        String busId,
        String placa,
        UUID sucursalId,
        LocalDate fechaServicio,
        BigDecimal totalManoObra,
        BigDecimal totalRepuestos,
        BigDecimal total,
        UUID registradoPor,
        Instant creadoEn,
        List<Linea> lineas) {

    public record Linea(long id, int categoriaId, String categoria, String descripcion, TipoLinea tipo,
            BigDecimal monto) {

        static Linea de(LineaGastoEntity l) {
            return new Linea(l.getId(), l.getCategoria().getId(), l.getCategoria().getNombre(), l.getDescripcion(),
                    l.getTipo(), l.getMonto());
        }
    }

    public static GastoTallerResponse de(GastoTallerEntity g) {
        return new GastoTallerResponse(g.getId(), g.getBusId(), g.getPlacaBus(), g.getSucursalId(),
                g.getFechaServicio(), g.getTotalManoObra(), g.getTotalRepuestos(), g.getTotal(), g.getRegistradoPor(),
                g.getCreadoEn(), g.getLineas().stream().map(Linea::de).toList());
    }
}
