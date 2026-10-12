package gt.cunoc.sa.finance.infrastructure.messaging;

import java.time.Duration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import gt.cunoc.sa.finance.infrastructure.messaging.eventos.TiposEvento;

/**
 * Exchanges y colas (README, "Topología en RabbitMQ"). RabbitAdmin los declara al conectar.
 *
 * Cada servicio declara los exchanges compartidos con exactamente los mismos argumentos: si
 * uno difiere, RabbitMQ rechaza la declaración (PRECONDITION_FAILED). Cada servicio declara
 * solo su propia cola; el productor no conoce a sus consumidores.
 */
@Configuration
public class TopologiaRabbit {

    public static final String EXCHANGE_EVENTOS = "transporte.events";
    public static final String EXCHANGE_DLX = "transporte.dlx";
    public static final String NO_ENRUTADOS = "transporte.unrouted";

    public static final String COLA = "finance.events";
    public static final String COLA_DLQ = COLA + ".dlq";

    /**
     * El alternate-exchange recibe lo que ninguna cola recibió (un evento cuyo consumidor no
     * se ha desplegado nunca). Sin él RabbitMQ lo descartaría en silencio, y en desarrollo deja
     * ver en la consola lo que se publicó mientras su consumidor todavía no existe.
     */
    @Bean
    TopicExchange eventos() {
        return ExchangeBuilder.topicExchange(EXCHANGE_EVENTOS).durable(true).alternate(NO_ENRUTADOS).build();
    }

    @Bean
    FanoutExchange noEnrutados() {
        return ExchangeBuilder.fanoutExchange(NO_ENRUTADOS).durable(true).build();
    }

    /** Con TTL: lo que nadie consume no crece para siempre (y E3/E17 llevan tokens). */
    @Bean
    Queue colaNoEnrutados() {
        return QueueBuilder.durable(NO_ENRUTADOS).ttl((int) Duration.ofDays(7).toMillis()).build();
    }

    @Bean
    Binding noEnrutadosACola() {
        return BindingBuilder.bind(colaNoEnrutados()).to(noEnrutados());
    }

    @Bean
    DirectExchange dlx() {
        return ExchangeBuilder.directExchange(EXCHANGE_DLX).durable(true).build();
    }

    /** Agotados los reintentos, el mensaje sale por el dlx con la routing key = nombre de la cola. */
    @Bean
    Queue cola() {
        return QueueBuilder.durable(COLA).deadLetterExchange(EXCHANGE_DLX).deadLetterRoutingKey(COLA).build();
    }

    @Bean
    Queue dlq() {
        return QueueBuilder.durable(COLA_DLQ).build();
    }

    @Bean
    Binding dlqAlDlx() {
        return BindingBuilder.bind(dlq()).to(dlx()).with(COLA);
    }

    @Bean
    Binding sucursalGuardada() {           // E1: réplica de sucursales para agrupar los reportes
        return enlazar(TiposEvento.SUCURSAL_GUARDADA);
    }

    @Bean
    Binding configuracionActualizada() {   // E2: tarifas de depreciación por km
        return enlazar(TiposEvento.CONFIGURACION_ACTUALIZADA);
    }

    @Bean
    Binding viajeFinalizado() {            // E9: costos del viaje
        return enlazar(TiposEvento.VIAJE_FINALIZADO);
    }

    @Bean
    Binding boletosVendidos() {            // E11: ingreso por boletos
        return enlazar(TiposEvento.BOLETOS_VENDIDOS);
    }

    @Bean
    Binding boletosAnulados() {            // E12: reverso del ingreso por boletos
        return enlazar(TiposEvento.BOLETOS_ANULADOS);
    }

    @Bean
    Binding alquilerPagado() {             // E14: ingreso por alquiler
        return enlazar(TiposEvento.ALQUILER_PAGADO);
    }

    @Bean
    Binding alquilerCancelado() {          // E16: reverso del ingreso por alquiler, si estaba pagado
        return enlazar(TiposEvento.ALQUILER_CANCELADO);
    }

    private Binding enlazar(String routingKey) {
        return BindingBuilder.bind(cola()).to(eventos()).with(routingKey);
    }
}
