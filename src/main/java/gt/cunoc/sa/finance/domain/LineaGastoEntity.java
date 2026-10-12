package gt.cunoc.sa.finance.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una línea del gasto: su categoría es obligatoria, y un mismo servicio reparte costos entre varias. */
@Entity
@Table(name = "linea_gasto")
public class LineaGastoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gasto_id", nullable = false)
    private GastoTallerEntity gasto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaGastoEntity categoria;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoLinea tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    protected LineaGastoEntity() {
    }

    static LineaGastoEntity nueva(GastoTallerEntity gasto, CategoriaGastoEntity categoria, String descripcion,
            TipoLinea tipo, BigDecimal monto) {
        LineaGastoEntity l = new LineaGastoEntity();
        l.gasto = gasto;
        l.categoria = categoria;
        l.descripcion = descripcion.strip();
        l.tipo = tipo;
        l.monto = monto;
        return l;
    }

    public Long getId() {
        return id;
    }

    public CategoriaGastoEntity getCategoria() {
        return categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public TipoLinea getTipo() {
        return tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}
