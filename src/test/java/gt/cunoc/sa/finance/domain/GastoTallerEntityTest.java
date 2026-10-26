package gt.cunoc.sa.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import gt.cunoc.sa.finance.domain.GastoTallerEntity.NuevaLinea;

class GastoTallerEntityTest {

    private final CategoriaGastoEntity motor = CategoriaGastoEntity.nueva("Motor");

    @Test
    void separaLosTotalesPorTipoYLosSuma() {
        GastoTallerEntity gasto = nuevo(List.of(
                new NuevaLinea(motor, "Empaque", TipoLinea.REPUESTO, new BigDecimal("85.50")),
                new NuevaLinea(motor, "Rectificación", TipoLinea.MANO_DE_OBRA, new BigDecimal("1500")),
                new NuevaLinea(motor, "Pistones", TipoLinea.REPUESTO, new BigDecimal("2300.25"))));

        assertThat(gasto.getTotalManoObra()).isEqualByComparingTo("1500.00");
        assertThat(gasto.getTotalRepuestos()).isEqualByComparingTo("2385.75");
        assertThat(gasto.getTotal()).isEqualByComparingTo("3885.75");
        assertThat(gasto.getLineas()).hasSize(3);
    }

    @Test
    void sinLineasDeUnTipoSuTotalEsCero() {
        GastoTallerEntity gasto = nuevo(List.of(new NuevaLinea(motor, "Diagnóstico", TipoLinea.MANO_DE_OBRA,
                new BigDecimal("200"))));

        assertThat(gasto.getTotalRepuestos()).isEqualByComparingTo("0");
        assertThat(gasto.getTotal()).isEqualByComparingTo("200");
    }

    @Test
    void unGastoSinLineasNoSeCrea() {
        assertThatThrownBy(() -> nuevo(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elNombreDeLaCategoriaSeGuardaSinEspaciosDeMas() {
        assertThat(CategoriaGastoEntity.nueva("  Cambio   de aceite ").getNombre()).isEqualTo("Cambio de aceite");
    }

    private static GastoTallerEntity nuevo(List<NuevaLinea> lineas) {
        return GastoTallerEntity.nuevo("7", "P123ABC", UUID.randomUUID(), LocalDate.of(2026, 10, 1),
                UUID.randomUUID(), lineas, Instant.now());
    }
}
