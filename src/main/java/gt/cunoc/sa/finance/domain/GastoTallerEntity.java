package gt.cunoc.sa.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Gasto de taller: encabezado (bus, fecha del servicio, totales) y sus líneas. Los totales los
 * calcula el sistema al crearlo y no hay edición: es parte del libro de costos, como los costos de
 * cada viaje.
 */
@Entity
@Table(name = "gasto_taller")
public class GastoTallerEntity extends ConIdAsignado<UUID> {

    @Id
    private UUID id;

    /** El id de fleet como texto (V3): fleet numera con bigserial. */
    @Column(name = "bus_id", nullable = false, length = 64)
    private String busId;

    @Column(name = "placa_bus", nullable = false, length = 20)
    private String placaBus;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "fecha_servicio", nullable = false)
    private LocalDate fechaServicio;

    @Column(name = "total_mano_obra", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalManoObra;

    @Column(name = "total_repuestos", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRepuestos;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "gasto", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<LineaGastoEntity> lineas = new ArrayList<>();

    protected GastoTallerEntity() {
    }

    /** Lo que trae cada línea, con la categoría ya validada (existe y está activa). */
    public record NuevaLinea(CategoriaGastoEntity categoria, String descripcion, TipoLinea tipo, BigDecimal monto) {
    }

    /**
     * @param placaBus   la que respondió fleet: los reportes la muestran sin preguntarle
     * @param sucursalId la del bus
     */
    public static GastoTallerEntity nuevo(String busId, String placaBus, UUID sucursalId, LocalDate fechaServicio,
            UUID registradoPor, List<NuevaLinea> lineas, Instant ahora) {
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("Un gasto de taller necesita al menos una línea");
        }
        GastoTallerEntity g = new GastoTallerEntity();
        g.id = UUID.randomUUID();
        g.busId = busId;
        g.placaBus = placaBus;
        g.sucursalId = sucursalId;
        g.fechaServicio = fechaServicio;
        g.registradoPor = registradoPor;
        g.creadoEn = ahora;
        lineas.forEach(l -> g.lineas.add(
                LineaGastoEntity.nueva(g, l.categoria(), l.descripcion(), l.tipo(), l.monto())));
        g.totalManoObra = suma(lineas, TipoLinea.MANO_DE_OBRA);
        g.totalRepuestos = suma(lineas, TipoLinea.REPUESTO);
        g.total = g.totalManoObra.add(g.totalRepuestos);
        return g;
    }

    private static BigDecimal suma(List<NuevaLinea> lineas, TipoLinea tipo) {
        return lineas.stream().filter(l -> l.tipo() == tipo).map(NuevaLinea::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getBusId() {
        return busId;
    }

    public String getPlacaBus() {
        return placaBus;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public LocalDate getFechaServicio() {
        return fechaServicio;
    }

    public BigDecimal getTotalManoObra() {
        return totalManoObra;
    }

    public BigDecimal getTotalRepuestos() {
        return totalRepuestos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public List<LineaGastoEntity> getLineas() {
        return lineas;
    }
}
