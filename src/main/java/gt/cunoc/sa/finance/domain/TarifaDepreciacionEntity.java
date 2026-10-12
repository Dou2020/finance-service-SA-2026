package gt.cunoc.sa.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réplica de una versión de la tarifa de depreciación por km (E2). El id es el de identity.tarifa:
 * lo inserta ReplicaTarifas con ON CONFLICT DO NOTHING, así un E2 repetido no duplica. Nunca se
 * actualiza: cada costo de viaje guarda la tarifa que usó.
 */
@Entity
@Immutable
@Table(name = "tarifa_depreciacion")
public class TarifaDepreciacionEntity {

    @Id
    private Long id;

    @Column(name = "monto_por_km", nullable = false, precision = 12, scale = 4)
    private BigDecimal montoPorKm;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    protected TarifaDepreciacionEntity() {
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getMontoPorKm() {
        return montoPorKm;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }
}
