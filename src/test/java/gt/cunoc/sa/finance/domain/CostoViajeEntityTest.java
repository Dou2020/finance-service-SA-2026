package gt.cunoc.sa.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CostoViajeEntityTest {

    @Test
    void laDepreciacionEsKmPorTarifaADosDecimales() {
        assertThat(CostoViajeEntity.depreciacion(250, new BigDecimal("1.5000"))).isEqualByComparingTo("375.00");
        assertThat(CostoViajeEntity.depreciacion(123, new BigDecimal("0.3333"))).isEqualByComparingTo("41.00");
        assertThat(CostoViajeEntity.depreciacion(0, new BigDecimal("2.0000"))).isEqualByComparingTo("0.00");
    }

    @Test
    void enAlquilerElChoferCobra15PorCientoMas() {
        assertThat(CostoViajeEntity.salario(TipoViaje.REGULAR, new BigDecimal("300"))).isEqualByComparingTo("300.00");
        assertThat(CostoViajeEntity.salario(TipoViaje.ALQUILER, new BigDecimal("300"))).isEqualByComparingTo("345.00");
        assertThat(CostoViajeEntity.salario(TipoViaje.ALQUILER, new BigDecimal("333.33")))
                .isEqualByComparingTo("383.33");
    }

    @Test
    void unKmFinalMenorQueElInicialNoSeRegistra() {
        CostoViajeEntity.Viaje viaje = new CostoViajeEntity.Viaje(UUID.randomUUID(), TipoViaje.REGULAR,
                UUID.randomUUID(), "P123ABC", UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 1), 1000,
                900, BigDecimal.TEN, BigDecimal.TEN);

        assertThatThrownBy(() -> CostoViajeEntity.calcular(viaje, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
