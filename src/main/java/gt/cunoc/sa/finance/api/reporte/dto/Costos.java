package gt.cunoc.sa.finance.api.reporte.dto;

import java.math.BigDecimal;

import gt.cunoc.sa.finance.domain.CostoViajeEntity;
import gt.cunoc.sa.finance.domain.GastoTallerEntity;

/**
 * Los gastos por concepto (enunciado: combustible, taller, repuestos y depreciación; más el salario
 * del chofer, supuesto 9), con su total. Una fila de viaje llena los tres primeros; una de taller,
 * mano de obra y repuestos.
 */
public record Costos(
        BigDecimal combustible,
        BigDecimal depreciacion,
        BigDecimal salarios,
        BigDecimal manoDeObra,
        BigDecimal repuestos,
        BigDecimal total) {

    public static final Costos CERO = new Costos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO);

    public static Costos de(CostoViajeEntity c) {
        return new Costos(c.getCombustible(), c.getDepreciacion(), c.getSalarioChofer(), BigDecimal.ZERO,
                BigDecimal.ZERO, c.total());
    }

    public static Costos de(GastoTallerEntity g) {
        return new Costos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, g.getTotalManoObra(),
                g.getTotalRepuestos(), g.getTotal());
    }

    public Costos mas(Costos otros) {
        return new Costos(combustible.add(otros.combustible), depreciacion.add(otros.depreciacion),
                salarios.add(otros.salarios), manoDeObra.add(otros.manoDeObra), repuestos.add(otros.repuestos),
                total.add(otros.total));
    }
}
