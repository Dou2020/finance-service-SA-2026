package gt.cunoc.sa.finance.application;

import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.api.reporte.CostoViajeRepository;
import gt.cunoc.sa.finance.api.reporte.SucursalReplicaRepository;
import gt.cunoc.sa.finance.api.reporte.TarifaDepreciacionRepository;
import gt.cunoc.sa.finance.domain.Calendario;
import gt.cunoc.sa.finance.domain.CostoViajeEntity;
import gt.cunoc.sa.finance.domain.TarifaDepreciacionEntity;
import gt.cunoc.sa.finance.infrastructure.messaging.Inbox;
import gt.cunoc.sa.finance.infrastructure.messaging.Payloads;
import gt.cunoc.sa.finance.infrastructure.messaging.Sobre;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.ViajeFinalizado;

/**
 * Aplica ViajeFinalizado (E9): los costos operativos del viaje, con la tarifa de depreciación vigente
 * a la fecha de llegada (README, supuesto 10), no a la de hoy ni a la de cuando llega el evento.
 */
@Service
public class CostosDeViaje {

    private static final Logger log = LoggerFactory.getLogger(CostosDeViaje.class);

    private final Inbox inbox;
    private final CostoViajeRepository costos;
    private final TarifaDepreciacionRepository tarifas;
    private final SucursalReplicaRepository sucursales;
    private final ObjectMapper json;

    public CostosDeViaje(Inbox inbox, CostoViajeRepository costos, TarifaDepreciacionRepository tarifas,
            SucursalReplicaRepository sucursales, ObjectMapper json) {
        this.inbox = inbox;
        this.costos = costos;
        this.tarifas = tarifas;
        this.sucursales = sucursales;
        this.json = json;
    }

    @Transactional
    public void registrar(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        ViajeFinalizado v = Payloads.leer(json, sobre, ViajeFinalizado.class);
        Payloads.exigir(v.viajeId() != null && v.tipo() != null && v.sucursalId() != null && v.busId() != null
                && v.placa() != null && v.choferId() != null && v.salarioBase() != null && v.kmInicial() != null
                && v.kmFinal() != null && v.combustible() != null && v.llegada() != null, sobre, "datos del viaje");
        // La llegada es inmutable en trip: un dato así no se va a corregir con un reintento.
        Payloads.exigir(v.kmFinal() >= v.kmInicial(), sobre, "km final mayor o igual al inicial");
        Payloads.exigir(v.combustible().signum() >= 0 && v.salarioBase().signum() >= 0, sobre,
                "combustible y salario no negativos");
        // trip registra la llegada una sola vez; un E9 con otro eventId sería una reemisión.
        if (costos.existsById(v.viajeId())) {
            log.info("Los costos del viaje {} ya estaban registrados; se descarta", v.viajeId());
            return;
        }
        if (!sucursales.existsById(v.sucursalId())) {
            throw new ReplicaPendienteException(
                    "La sucursal " + v.sucursalId() + " todavía no está replicada en finance");
        }
        LocalDate fecha = LocalDate.ofInstant(v.llegada(), Calendario.ZONA);
        TarifaDepreciacionEntity tarifa = tarifas.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fecha)
                .orElseThrow(() -> new ReplicaPendienteException(
                        "No hay tarifa de depreciación vigente al " + fecha + " (viaje " + v.viajeId() + ")"));
        costos.save(CostoViajeEntity.calcular(new CostoViajeEntity.Viaje(v.viajeId(), v.tipo(), v.busId(), v.placa(),
                v.choferId(), v.sucursalId(), fecha, v.kmInicial(), v.kmFinal(), v.combustible(), v.salarioBase()),
                tarifa, Instant.now()));
    }
}
