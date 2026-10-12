package gt.cunoc.sa.finance.infrastructure.messaging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.application.CostosDeViaje;
import gt.cunoc.sa.finance.application.LibroIngresos;
import gt.cunoc.sa.finance.application.ReplicaSucursales;
import gt.cunoc.sa.finance.application.ReplicaTarifas;
import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;
import gt.cunoc.sa.finance.infrastructure.web.CorrelationIdFilter;

/**
 * Consume finance.events. Lee el sobre a mano (no con un MessageConverter) para que un cuerpo
 * ilegible sea un EventoNoProcesable que va directo a la DLQ.
 */
@Component
public class ListenerEventos {

    private static final Logger log = LoggerFactory.getLogger(ListenerEventos.class);

    private final ObjectMapper json;
    private final ReplicaSucursales replicaSucursales;
    private final ReplicaTarifas replicaTarifas;
    private final CostosDeViaje costosDeViaje;
    private final LibroIngresos libroIngresos;

    public ListenerEventos(ObjectMapper json, ReplicaSucursales replicaSucursales, ReplicaTarifas replicaTarifas,
            CostosDeViaje costosDeViaje, LibroIngresos libroIngresos) {
        this.json = json;
        this.replicaSucursales = replicaSucursales;
        this.replicaTarifas = replicaTarifas;
        this.costosDeViaje = costosDeViaje;
        this.libroIngresos = libroIngresos;
    }

    @RabbitListener(queues = TopologiaRabbit.COLA)
    public void recibir(Message mensaje) {
        Sobre sobre = leer(mensaje);
        // El mismo X-Correlation-Id del request que originó el evento: el flujo se sigue en
        // los logs de todos los servicios que tocó.
        if (sobre.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_CLAVE, sobre.correlationId());
        }
        try {
            switch (sobre.type()) {
                case TiposEvento.SUCURSAL_GUARDADA -> replicaSucursales.aplicar(sobre);
                case TiposEvento.CONFIGURACION_ACTUALIZADA -> replicaTarifas.aplicar(sobre);
                case TiposEvento.VIAJE_FINALIZADO -> costosDeViaje.registrar(sobre);
                case TiposEvento.BOLETOS_VENDIDOS -> libroIngresos.boletosVendidos(sobre);
                case TiposEvento.BOLETOS_ANULADOS -> libroIngresos.boletosAnulados(sobre);
                case TiposEvento.ALQUILER_PAGADO -> libroIngresos.alquilerPagado(sobre);
                case TiposEvento.ALQUILER_CANCELADO -> libroIngresos.alquilerCancelado(sobre);
                // La cola solo se enlaza a lo que este servicio consume; si llega otra cosa es
                // un binding de más, no un error del mensaje.
                default -> log.warn("Evento {} sin manejador en finance; se descarta", sobre.type());
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_CLAVE);
        }
    }

    private Sobre leer(Message mensaje) {
        Sobre sobre;
        try {
            sobre = json.readValue(mensaje.getBody(), Sobre.class);
        } catch (IOException e) {
            throw new EventoNoProcesableException("Sobre ilegible: " + e.getMessage(), e);
        }
        if (sobre.eventId() == null || sobre.type() == null || sobre.payload() == null) {
            throw new EventoNoProcesableException("Sobre sin eventId, type o payload");
        }
        return sobre;
    }
}
