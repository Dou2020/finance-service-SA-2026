package gt.cunoc.sa.finance.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.api.reporte.SucursalReplicaRepository;
import gt.cunoc.sa.finance.infrastructure.messaging.Inbox;
import gt.cunoc.sa.finance.infrastructure.messaging.Payloads;
import gt.cunoc.sa.finance.infrastructure.messaging.Sobre;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.SucursalGuardada;

/**
 * Aplica SucursalGuardada (E1): la réplica con la que los reportes agrupan por sucursal, en orden
 * alfabético, sin preguntarle a identity. Una sucursal desactivada se conserva: sus ingresos y
 * costos siguen apareciendo en los reportes de su período.
 */
@Service
public class ReplicaSucursales {

    private static final Logger log = LoggerFactory.getLogger(ReplicaSucursales.class);

    private final Inbox inbox;
    private final SucursalReplicaRepository sucursales;
    private final ObjectMapper json;

    public ReplicaSucursales(Inbox inbox, SucursalReplicaRepository sucursales, ObjectMapper json) {
        this.inbox = inbox;
        this.sucursales = sucursales;
        this.json = json;
    }

    @Transactional
    public void aplicar(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        SucursalGuardada s = Payloads.leer(json, sobre, SucursalGuardada.class);
        Payloads.exigir(s.sucursalId() != null && s.nombre() != null && s.activa() != null, sobre,
                "sucursalId, nombre o activa");
        if (sucursales.guardar(s.sucursalId(), s.nombre(), s.activa(), sobre.aggregateVersion()) == 0) {
            log.info("SucursalGuardada v{} de {} ya superada; se descarta", sobre.aggregateVersion(), s.sucursalId());
        }
    }
}
