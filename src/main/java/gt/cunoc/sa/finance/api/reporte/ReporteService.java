package gt.cunoc.sa.finance.api.reporte;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.finance.api.gasto.GastoTallerRepository;
import gt.cunoc.sa.finance.api.reporte.dto.Costos;
import gt.cunoc.sa.finance.api.reporte.dto.Ingresos;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteDepreciacionResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteGananciasResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteGastosResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteIngresosResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteTallerPorBusResponse;
import gt.cunoc.sa.finance.api.reporte.dto.ReporteTallerPorCategoriaResponse;
import gt.cunoc.sa.finance.application.Alcance;
import gt.cunoc.sa.finance.application.Periodo;
import gt.cunoc.sa.finance.domain.Calendario;
import gt.cunoc.sa.finance.domain.CostoViajeEntity;
import gt.cunoc.sa.finance.domain.GastoTallerEntity;
import gt.cunoc.sa.finance.domain.IngresoEntity;
import gt.cunoc.sa.finance.domain.SucursalReplicaEntity;
import gt.cunoc.sa.finance.domain.TarifaDepreciacionEntity;
import gt.cunoc.sa.finance.domain.TipoLinea;
import gt.cunoc.sa.finance.domain.TipoViaje;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;

/**
 * Los 6 reportes de finance (README, "Reportes"). Filtran por la fecha de negocio (la de pago, la de
 * llegada, la del servicio), y sin intervalo toman todo. Un SYS_ADMIN ve todas las sucursales o la
 * que pida; un BRANCH_ADMIN, siempre la suya (el mismo criterio que el reporte de alquileres de
 * customer). Las consultas traen las filas del período y el agrupado se hace aquí: el volumen es el
 * de una empresa de buses, y así los subtotales salen de las mismas filas que se muestran.
 */
@Service
public class ReporteService {

    private static final String SOLO_ADMINS = "hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')";

    /**
     * Orden alfabético en español. La base no sirve para esto: su en_US.utf8 corre sobre musl (imagen
     * alpine), que compara por bytes, con las mayúsculas antes que las minúsculas y "Ávila" después de
     * "zacapa".
     */
    private static final Comparator<String> ORDEN = Collator.getInstance(Locale.forLanguageTag("es"))::compare;

    private final IngresoRepository ingresos;
    private final CostoViajeRepository costos;
    private final GastoTallerRepository gastos;
    private final LineaGastoRepository lineas;
    private final SucursalReplicaRepository sucursales;
    private final TarifaDepreciacionRepository tarifas;

    public ReporteService(IngresoRepository ingresos, CostoViajeRepository costos, GastoTallerRepository gastos,
            LineaGastoRepository lineas, SucursalReplicaRepository sucursales, TarifaDepreciacionRepository tarifas) {
        this.ingresos = ingresos;
        this.costos = costos;
        this.gastos = gastos;
        this.lineas = lineas;
        this.sucursales = sucursales;
        this.tarifas = tarifas;
    }

    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteIngresosResponse ingresos(Periodo periodo, UUID sucursalId, UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<UUID, List<IngresoEntity>> porSucursal = agrupar(
                ingresos.vigentes(alcance, periodo.inicio(), periodo.fin()), IngresoEntity::getSucursalId);
        List<ReporteIngresosResponse.Sucursal> lista = nombres.enOrden(porSucursal.keySet()).stream().map(id -> {
            List<IngresoEntity> suyos = porSucursal.get(id);
            return new ReporteIngresosResponse.Sucursal(id, nombres.nombre(id),
                    suyos.stream().map(i -> new ReporteIngresosResponse.Fila(i.getFecha(), i.getTipo(),
                            i.getReferenciaId(), i.getDetalle(), i.getMonto())).toList(),
                    suyos.stream().map(Ingresos::de).reduce(Ingresos.CERO, Ingresos::mas));
        }).toList();
        return new ReporteIngresosResponse(periodo.desde(), periodo.hasta(), alcance, lista,
                lista.stream().map(ReporteIngresosResponse.Sucursal::subtotal).reduce(Ingresos.CERO, Ingresos::mas));
    }

    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteGastosResponse gastos(Periodo periodo, UUID sucursalId, UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<UUID, List<ReporteGastosResponse.Fila>> porSucursal = new LinkedHashMap<>();
        for (CostoViajeEntity c : costos.delPeriodo(alcance, periodo.inicio(), periodo.fin())) {
            porSucursal.computeIfAbsent(c.getSucursalId(), k -> new ArrayList<>()).add(new ReporteGastosResponse.Fila(
                    c.getFecha(), "VIAJE", c.getId(), c.getPlacaBus(), detalle(c), Costos.de(c)));
        }
        for (GastoTallerEntity g : gastos.delPeriodo(alcance, periodo.inicio(), periodo.fin())) {
            porSucursal.computeIfAbsent(g.getSucursalId(), k -> new ArrayList<>()).add(new ReporteGastosResponse.Fila(
                    g.getFechaServicio(), "TALLER", g.getId(), g.getPlacaBus(), "Gasto de taller", Costos.de(g)));
        }
        List<ReporteGastosResponse.Sucursal> lista = nombres.enOrden(porSucursal.keySet()).stream().map(id -> {
            List<ReporteGastosResponse.Fila> filas = porSucursal.get(id).stream()
                    .sorted(Comparator.comparing(ReporteGastosResponse.Fila::fecha)).toList();
            return new ReporteGastosResponse.Sucursal(id, nombres.nombre(id), filas,
                    filas.stream().map(ReporteGastosResponse.Fila::costos).reduce(Costos.CERO, Costos::mas));
        }).toList();
        return new ReporteGastosResponse(periodo.desde(), periodo.hasta(), alcance, lista,
                lista.stream().map(ReporteGastosResponse.Sucursal::subtotal).reduce(Costos.CERO, Costos::mas));
    }

    /** Todas las sucursales activas y las inactivas con movimiento, aunque no tengan ni ingresos ni gastos. */
    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteGananciasResponse ganancias(Periodo periodo, UUID sucursalId, UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<UUID, Ingresos> ingresosDe = new LinkedHashMap<>();
        ingresos.vigentes(alcance, periodo.inicio(), periodo.fin())
                .forEach(i -> ingresosDe.merge(i.getSucursalId(), Ingresos.de(i), Ingresos::mas));
        Map<UUID, Costos> costosDe = new LinkedHashMap<>();
        costos.delPeriodo(alcance, periodo.inicio(), periodo.fin())
                .forEach(c -> costosDe.merge(c.getSucursalId(), Costos.de(c), Costos::mas));
        gastos.delPeriodo(alcance, periodo.inicio(), periodo.fin())
                .forEach(g -> costosDe.merge(g.getSucursalId(), Costos.de(g), Costos::mas));

        Set<UUID> incluidas = new HashSet<>(ingresosDe.keySet());
        incluidas.addAll(costosDe.keySet());
        nombres.activas().stream().filter(id -> alcance == null || alcance.equals(id)).forEach(incluidas::add);
        List<ReporteGananciasResponse.Sucursal> lista = nombres.enOrden(incluidas).stream().map(id -> {
            Ingresos i = ingresosDe.getOrDefault(id, Ingresos.CERO);
            Costos c = costosDe.getOrDefault(id, Costos.CERO);
            return new ReporteGananciasResponse.Sucursal(id, nombres.nombre(id), i, c, i.total().subtract(c.total()));
        }).toList();
        Ingresos totalIngresos = lista.stream().map(ReporteGananciasResponse.Sucursal::ingresos)
                .reduce(Ingresos.CERO, Ingresos::mas);
        Costos totalCostos = lista.stream().map(ReporteGananciasResponse.Sucursal::costos)
                .reduce(Costos.CERO, Costos::mas);
        return new ReporteGananciasResponse(periodo.desde(), periodo.hasta(), alcance, lista, totalIngresos,
                totalCostos, totalIngresos.total().subtract(totalCostos.total()));
    }

    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteTallerPorCategoriaResponse tallerPorCategoria(Periodo periodo, UUID sucursalId, UUID busId,
            UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<Integer, List<LineaDeTaller>> porCategoria = new LinkedHashMap<>();
        lineas.delPeriodo(alcance, busId, periodo.inicio(), periodo.fin())
                .forEach(l -> porCategoria.computeIfAbsent(l.categoriaId(), k -> new ArrayList<>()).add(l));
        List<ReporteTallerPorCategoriaResponse.Categoria> categorias = porCategoria.values().stream()
                .map(suyas -> new ReporteTallerPorCategoriaResponse.Categoria(suyas.get(0).categoriaId(),
                        suyas.get(0).categoria(),
                        suyas.stream().map(l -> new ReporteTallerPorCategoriaResponse.Fila(l.gastoId(), l.placa(),
                                nombres.nombre(l.sucursalId()), l.fecha(), l.descripcion(), l.tipo(), l.monto()))
                                .toList(),
                        suma(suyas, LineaDeTaller::monto)))
                .sorted(Comparator.comparing(ReporteTallerPorCategoriaResponse.Categoria::nombre, ORDEN))
                .toList();
        return new ReporteTallerPorCategoriaResponse(periodo.desde(), periodo.hasta(), alcance, busId, categorias,
                suma(categorias, ReporteTallerPorCategoriaResponse.Categoria::total));
    }

    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteTallerPorBusResponse tallerPorBus(Periodo periodo, UUID sucursalId, UUID busId,
            UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<UUID, List<LineaDeTaller>> porBus = agrupar(lineas.delPeriodo(alcance, busId, periodo.inicio(),
                periodo.fin()), LineaDeTaller::busId);
        List<ReporteTallerPorBusResponse.Bus> buses = porBus.values().stream().map(suyas -> {
            Map<String, List<LineaDeTaller>> porCategoria = new LinkedHashMap<>();
            suyas.forEach(l -> porCategoria.computeIfAbsent(l.categoria(), k -> new ArrayList<>()).add(l));
            List<ReporteTallerPorBusResponse.Categoria> categorias = porCategoria.entrySet().stream()
                    .map(e -> new ReporteTallerPorBusResponse.Categoria(e.getKey(),
                            suma(e.getValue(), TipoLinea.MANO_DE_OBRA), suma(e.getValue(), TipoLinea.REPUESTO),
                            suma(e.getValue(), LineaDeTaller::monto)))
                    .sorted(Comparator.comparing(ReporteTallerPorBusResponse.Categoria::nombre, ORDEN))
                    .toList();
            LineaDeTaller ultima = suyas.get(suyas.size() - 1);
            return new ReporteTallerPorBusResponse.Bus(ultima.busId(), ultima.placa(),
                    nombres.nombre(ultima.sucursalId()),
                    suyas.stream().map(LineaDeTaller::gastoId).distinct().count(), categorias,
                    suma(suyas, TipoLinea.MANO_DE_OBRA), suma(suyas, TipoLinea.REPUESTO),
                    suma(suyas, LineaDeTaller::monto));
        }).sorted(Comparator.comparing(ReporteTallerPorBusResponse.Bus::placa)).toList();
        return new ReporteTallerPorBusResponse(periodo.desde(), periodo.hasta(), alcance, busId, buses,
                suma(buses, ReporteTallerPorBusResponse.Bus::total));
    }

    @PreAuthorize(SOLO_ADMINS)
    @Transactional(readOnly = true)
    public ReporteDepreciacionResponse depreciacion(Periodo periodo, UUID sucursalId, UsuarioActual actual) {
        UUID alcance = alcance(sucursalId, actual);
        Sucursales nombres = sucursales();
        Map<UUID, List<CostoViajeEntity>> porBus = agrupar(
                costos.delPeriodo(alcance, periodo.inicio(), periodo.fin()), CostoViajeEntity::getBusId);
        List<ReporteDepreciacionResponse.Bus> buses = porBus.values().stream().map(viajes -> {
            CostoViajeEntity ultimo = viajes.get(viajes.size() - 1);
            return new ReporteDepreciacionResponse.Bus(ultimo.getBusId(), ultimo.getPlacaBus(),
                    nombres.nombre(ultimo.getSucursalId()), viajes.size(),
                    viajes.stream().mapToLong(CostoViajeEntity::getKmRecorridos).sum(),
                    suma(viajes, CostoViajeEntity::getDepreciacion));
        }).sorted(Comparator.comparing(ReporteDepreciacionResponse.Bus::placa)).toList();
        TarifaDepreciacionEntity vigente = tarifas
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(Calendario.hoy()).orElse(null);
        return new ReporteDepreciacionResponse(periodo.desde(), periodo.hasta(), alcance,
                vigente == null ? null : vigente.getMontoPorKm(), vigente == null ? null : vigente.getVigenteDesde(),
                buses, buses.stream().mapToLong(ReporteDepreciacionResponse.Bus::viajes).sum(),
                buses.stream().mapToLong(ReporteDepreciacionResponse.Bus::kmRecorridos).sum(),
                suma(buses, ReporteDepreciacionResponse.Bus::depreciacion));
    }

    /** Un SYS_ADMIN, todas o la que pida; los demás, siempre la suya aunque pidan otra. */
    private static UUID alcance(UUID sucursalId, UsuarioActual actual) {
        return Alcance.esSysAdmin(actual) ? sucursalId : Alcance.sucursalDe(actual);
    }

    private static String detalle(CostoViajeEntity c) {
        return (c.getTipoViaje() == TipoViaje.ALQUILER ? "Viaje de alquiler" : "Viaje regular") + " · "
                + c.getKmRecorridos() + " km";
    }

    private static <T> Map<UUID, List<T>> agrupar(List<T> filas, Function<T, UUID> clave) {
        Map<UUID, List<T>> grupos = new LinkedHashMap<>();
        filas.forEach(f -> grupos.computeIfAbsent(clave.apply(f), k -> new ArrayList<>()).add(f));
        return grupos;
    }

    private static <T> BigDecimal suma(Collection<T> filas, Function<T, BigDecimal> monto) {
        return filas.stream().map(monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal suma(List<LineaDeTaller> filas, TipoLinea tipo) {
        return suma(filas.stream().filter(l -> l.tipo() == tipo).toList(), LineaDeTaller::monto);
    }

    private Sucursales sucursales() {
        Map<UUID, SucursalReplicaEntity> porId = new LinkedHashMap<>();
        sucursales.findAll().forEach(s -> porId.put(s.getId(), s));
        return new Sucursales(porId);
    }

    /** La réplica de sucursales para poner nombres y ordenar. */
    private record Sucursales(Map<UUID, SucursalReplicaEntity> porId) {

        /** La FK garantiza que existe; el id solo saldría si alguien la borrara a mano. */
        String nombre(UUID id) {
            SucursalReplicaEntity s = porId.get(id);
            return s == null ? id.toString() : s.getNombre();
        }

        List<UUID> activas() {
            return porId.values().stream().filter(SucursalReplicaEntity::isActiva).map(SucursalReplicaEntity::getId)
                    .toList();
        }

        List<UUID> enOrden(Collection<UUID> ids) {
            return ids.stream().sorted(Comparator.comparing(this::nombre, ORDEN)).toList();
        }
    }
}
