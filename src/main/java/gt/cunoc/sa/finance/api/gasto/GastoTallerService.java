package gt.cunoc.sa.finance.api.gasto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import gt.cunoc.sa.finance.api.categoria.CategoriaRepository;
import gt.cunoc.sa.finance.api.error.ErrorDeNegocio;
import gt.cunoc.sa.finance.api.error.ManejadorErrores;
import gt.cunoc.sa.finance.api.gasto.dto.GastoTallerRequest;
import gt.cunoc.sa.finance.api.gasto.dto.GastoTallerResponse;
import gt.cunoc.sa.finance.api.gasto.dto.LineaGastoRequest;
import gt.cunoc.sa.finance.api.gasto.dto.ResumenGastoResponse;
import gt.cunoc.sa.finance.api.reporte.SucursalReplicaRepository;
import gt.cunoc.sa.finance.application.Alcance;
import gt.cunoc.sa.finance.application.Periodo;
import gt.cunoc.sa.finance.domain.Calendario;
import gt.cunoc.sa.finance.domain.CategoriaGastoEntity;
import gt.cunoc.sa.finance.domain.GastoTallerEntity;
import gt.cunoc.sa.finance.domain.GastoTallerEntity.NuevaLinea;
import gt.cunoc.sa.finance.infrastructure.client.BusRemoto;
import gt.cunoc.sa.finance.infrastructure.client.FleetClient;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;
import jakarta.persistence.criteria.Predicate;

/**
 * Gastos de taller (README, "Taller y categorías"). Los registra el BRANCH_ADMIN para los buses de
 * su sucursal; fleet (S6) dice si el bus existe y de qué sucursal es.
 */
@Service
public class GastoTallerService {

    private final GastoTallerRepository gastos;
    private final CategoriaRepository categorias;
    private final SucursalReplicaRepository sucursales;
    private final FleetClient fleet;
    private final TransactionTemplate transacciones;

    public GastoTallerService(GastoTallerRepository gastos, CategoriaRepository categorias,
            SucursalReplicaRepository sucursales, FleetClient fleet, TransactionTemplate transacciones) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.sucursales = sucursales;
        this.fleet = fleet;
        this.transacciones = transacciones;
    }

    /**
     * S6 va antes de la transacción: con el reintento, fleet puede tardar hasta unos 4 s, y mientras
     * tanto no se retiene una conexión de la base. Si fleet no responde, no se guarda nada (503).
     */
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public GastoTallerResponse registrar(GastoTallerRequest solicitud, UsuarioActual actual) {
        LocalDate fecha = solicitud.fechaServicio() == null ? Calendario.hoy() : solicitud.fechaServicio();
        if (fecha.isAfter(Calendario.hoy())) {
            throw ErrorDeNegocio.noProcesable("La fecha del servicio no puede ser posterior a hoy");
        }
        // Sin los espacios de los extremos: fleet no reconocería " 7". Se guarda el id que responde fleet.
        BusRemoto bus = fleet.bus(solicitud.busId().strip());
        Alcance.exigirSucursal(actual, bus.sucursalId());
        return transacciones.execute(estado -> guardar(solicitud, bus, fecha, actual));
    }

    private GastoTallerResponse guardar(GastoTallerRequest solicitud, BusRemoto bus, LocalDate fecha,
            UsuarioActual actual) {
        // La réplica llega por E1; sin ella la FK fallaría con un mensaje menos claro.
        if (!sucursales.existsById(bus.sucursalId())) {
            throw ErrorDeNegocio.conflicto(ManejadorErrores.SUCURSAL_SIN_REPLICA);
        }
        Map<Integer, CategoriaGastoEntity> usadas = categorias.bloquearParaUso(
                solicitud.lineas().stream().map(LineaGastoRequest::categoriaId).distinct().toList())
                .stream().collect(Collectors.toMap(CategoriaGastoEntity::getId, Function.identity()));
        List<NuevaLinea> lineas = new ArrayList<>();
        for (LineaGastoRequest linea : solicitud.lineas()) {
            CategoriaGastoEntity categoria = usadas.get(linea.categoriaId());
            if (categoria == null) {
                throw ErrorDeNegocio.noProcesable("No existe la categoría " + linea.categoriaId());
            }
            if (!categoria.isActiva()) {
                throw ErrorDeNegocio.noProcesable("La categoría " + categoria.getNombre() + " está desactivada");
            }
            lineas.add(new NuevaLinea(categoria, linea.descripcion(), linea.tipo(), linea.monto()));
        }
        GastoTallerEntity gasto = GastoTallerEntity.nuevo(bus.id(), bus.placa(), bus.sucursalId(), fecha,
                actual.usuarioId(), lineas, Instant.now());
        gastos.saveAndFlush(gasto);
        return GastoTallerResponse.de(gasto);
    }

    /** El filtro de sucursal va en la consulta: un BRANCH_ADMIN nunca recibe filas de otra. */
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional(readOnly = true)
    public Page<ResumenGastoResponse> listar(UUID sucursalId, String busId, Periodo periodo, Pageable pagina,
            UsuarioActual actual) {
        UUID alcance = Alcance.esSysAdmin(actual) ? sucursalId : Alcance.sucursalDe(actual);
        // Vacío es "sin filtro", como hacía la conversión a UUID antes de V3 (ReporteService igual).
        String bus = StringUtils.hasText(busId) ? busId.strip() : null;
        Specification<GastoTallerEntity> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (alcance != null) {
                condiciones.add(cb.equal(raiz.get("sucursalId"), alcance));
            }
            if (bus != null) {
                condiciones.add(cb.equal(raiz.get("busId"), bus));
            }
            if (periodo.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaServicio"), periodo.desde()));
            }
            if (periodo.hasta() != null) {
                condiciones.add(cb.lessThanOrEqualTo(raiz.get("fechaServicio"), periodo.hasta()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return gastos.findAll(filtro, pagina).map(ResumenGastoResponse::de);
    }

    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional(readOnly = true)
    public GastoTallerResponse obtener(UUID id, UsuarioActual actual) {
        GastoTallerEntity gasto = gastos.conLineas(id)
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe ese gasto de taller"));
        Alcance.exigirSucursal(actual, gasto.getSucursalId());
        return GastoTallerResponse.de(gasto);
    }
}
