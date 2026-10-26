package gt.cunoc.sa.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un ingreso del libro: una venta de boletos (E11) o un alquiler pagado (E14), en la sucursal donde
 * se reconoce y en su fecha de pago. El monto nunca cambia; la anulación (E12) o la cancelación (E16)
 * solo lo marcan reversado. Lo escribe LibroIngresos con sentencias atómicas (IngresoRepository); aquí
 * es de solo lectura, para los reportes.
 */
@Entity
@Immutable
@Table(name = "ingreso")
public class IngresoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoIngreso tipo;

    /** La venta de sales (su id como texto) o el alquiler de customer (su UUID como texto). */
    @Column(name = "referencia_id", nullable = false, length = 64)
    private String referenciaId;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 200)
    private String detalle;

    @Column(nullable = false)
    private boolean reversado;

    @Column(name = "reversado_en")
    private Instant reversadoEn;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected IngresoEntity() {
    }

    public Long getId() {
        return id;
    }

    public TipoIngreso getTipo() {
        return tipo;
    }

    public String getReferenciaId() {
        return referenciaId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getDetalle() {
        return detalle;
    }

    public boolean isReversado() {
        return reversado;
    }

    public Instant getReversadoEn() {
        return reversadoEn;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
