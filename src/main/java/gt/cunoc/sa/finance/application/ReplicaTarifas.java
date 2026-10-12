package gt.cunoc.sa.finance.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.api.reporte.TarifaDepreciacionRepository;
import gt.cunoc.sa.finance.infrastructure.messaging.Inbox;
import gt.cunoc.sa.finance.infrastructure.messaging.Payloads;
import gt.cunoc.sa.finance.infrastructure.messaging.Sobre;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.ConfiguracionActualizada;

/**
 * Aplica ConfiguracionActualizada (E2): guarda cada versión de la tarifa de depreciación, no solo
 * la última, porque el costo de un viaje usa la vigente a su fecha de llegada, que puede ser pasada.
 * Las de alquiler son de customer.
 */
@Service
public class ReplicaTarifas {

    /** El nombre de TipoTarifa en identity; no se copia el enum para no acoplarse a los demás tipos. */
    static final String DEPRECIACION_KM = "DEPRECIACION_KM";

    private static final Logger log = LoggerFactory.getLogger(ReplicaTarifas.class);

    private final Inbox inbox;
    private final TarifaDepreciacionRepository tarifas;
    private final ObjectMapper json;

    public ReplicaTarifas(Inbox inbox, TarifaDepreciacionRepository tarifas, ObjectMapper json) {
        this.inbox = inbox;
        this.tarifas = tarifas;
        this.json = json;
    }

    @Transactional
    public void aplicar(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        ConfiguracionActualizada tarifa = Payloads.leer(json, sobre, ConfiguracionActualizada.class);
        Payloads.exigir(tarifa.tipo() != null && tarifa.valor() != null && tarifa.vigenteDesde() != null, sobre,
                "tipo, valor o vigenteDesde");
        if (!DEPRECIACION_KM.equals(tarifa.tipo())) {
            log.debug("Tarifa {} no es de depreciación; se descarta", tarifa.tipo());
            return;
        }
        // Por id de identity: la misma versión que llega otra vez (otro eventId) no se duplica.
        tarifas.insertar(tarifa.tarifaId(), tarifa.valor(), tarifa.vigenteDesde());
    }
}
