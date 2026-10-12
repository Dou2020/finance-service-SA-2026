package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;

import gt.cunoc.sa.finance.domain.IngresoEntity;
import gt.cunoc.sa.finance.domain.TipoIngreso;

/** Ingresos separados por origen, con su total: subtotal de una sucursal o total global. */
public record Ingresos(BigDecimal boletos, BigDecimal alquileres, BigDecimal total) {

    public static final Ingresos CERO = new Ingresos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public static Ingresos de(IngresoEntity i) {
        return i.getTipo() == TipoIngreso.BOLETO
                ? new Ingresos(i.getMonto(), BigDecimal.ZERO, i.getMonto())
                : new Ingresos(BigDecimal.ZERO, i.getMonto(), i.getMonto());
    }

    public Ingresos mas(Ingresos otros) {
        return new Ingresos(boletos.add(otros.boletos), alquileres.add(otros.alquileres), total.add(otros.total));
    }
}
