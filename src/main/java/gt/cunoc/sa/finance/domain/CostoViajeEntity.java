package gt.cunoc.sa.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Los costos operativos de un viaje finalizado (E9): combustible, depreciación y salario del chofer
 * (README, "Salida, llegada y costos del viaje"). Se guardan calculados, con la tarifa que se aplicó:
 * cambiar la tarifa después no altera lo registrado (enunciado, "Rutas y viajes").
 */
@Entity
@Table(name = "costo_viaje")
public class CostoViajeEntity extends ConIdAsignado {

    /** En un viaje de alquiler el chofer cobra 15 % sobre su salario base (enunciado). */
    static final BigDecimal RECARGO_ALQUILER = new BigDecimal("1.15");

    @Id
    @Column(name = "viaje_id")
    private UUID viajeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_viaje", nullable = false, length = 10)
    private TipoViaje tipoViaje;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "placa_bus", nullable = false, length = 10)
    private String placaBus;

    @Column(name = "chofer_id", nullable = false)
    private UUID choferId;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "km_recorridos", nullable = false)
    private int kmRecorridos;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal combustible;

    @Column(name = "tarifa_id", nullable = false)
    private Long tarifaId;

    @Column(name = "tarifa_aplicada", nullable = false, precision = 12, scale = 4)
    private BigDecimal tarifaAplicada;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal depreciacion;

    @Column(name = "salario_chofer", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioChofer;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected CostoViajeEntity() {
    }

    /** Lo que trae ViajeFinalizado, ya validado. */
    public record Viaje(UUID viajeId, TipoViaje tipo, UUID busId, String placa, UUID choferId, UUID sucursalId,
            LocalDate fechaLlegada, int kmInicial, int kmFinal, BigDecimal combustible, BigDecimal salarioBase) {
    }

    /** @param tarifa la vigente a la fecha de llegada */
    public static CostoViajeEntity calcular(Viaje viaje, TarifaDepreciacionEntity tarifa, Instant ahora) {
        if (viaje.kmFinal() < viaje.kmInicial()) {
            throw new IllegalArgumentException("El km final no puede ser menor que el inicial");
        }
        CostoViajeEntity c = new CostoViajeEntity();
        c.viajeId = viaje.viajeId();
        c.tipoViaje = viaje.tipo();
        c.busId = viaje.busId();
        c.placaBus = viaje.placa();
        c.choferId = viaje.choferId();
        c.sucursalId = viaje.sucursalId();
        c.fecha = viaje.fechaLlegada();
        c.kmRecorridos = viaje.kmFinal() - viaje.kmInicial();
        c.combustible = viaje.combustible().setScale(2, RoundingMode.HALF_UP);
        c.tarifaId = tarifa.getId();
        c.tarifaAplicada = tarifa.getMontoPorKm();
        c.depreciacion = depreciacion(c.kmRecorridos, tarifa.getMontoPorKm());
        c.salarioChofer = salario(viaje.tipo(), viaje.salarioBase());
        c.registradoEn = ahora;
        return c;
    }

    static BigDecimal depreciacion(int km, BigDecimal tarifaPorKm) {
        return tarifaPorKm.multiply(BigDecimal.valueOf(km)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal salario(TipoViaje tipo, BigDecimal base) {
        BigDecimal salario = tipo == TipoViaje.ALQUILER ? base.multiply(RECARGO_ALQUILER) : base;
        return salario.setScale(2, RoundingMode.HALF_UP);
    }

    /** Lo que costó el viaje: combustible, depreciación y salario. */
    public BigDecimal total() {
        return combustible.add(depreciacion).add(salarioChofer);
    }

    @Override
    public UUID getId() {
        return viajeId;
    }

    public TipoViaje getTipoViaje() {
        return tipoViaje;
    }

    public UUID getBusId() {
        return busId;
    }

    public String getPlacaBus() {
        return placaBus;
    }

    public UUID getChoferId() {
        return choferId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public int getKmRecorridos() {
        return kmRecorridos;
    }

    public BigDecimal getCombustible() {
        return combustible;
    }

    public Long getTarifaId() {
        return tarifaId;
    }

    public BigDecimal getTarifaAplicada() {
        return tarifaAplicada;
    }

    public BigDecimal getDepreciacion() {
        return depreciacion;
    }

    public BigDecimal getSalarioChofer() {
        return salarioChofer;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
