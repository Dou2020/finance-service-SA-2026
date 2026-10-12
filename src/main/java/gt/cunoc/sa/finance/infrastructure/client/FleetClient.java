package gt.cunoc.sa.finance.infrastructure.client;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import gt.cunoc.sa.finance.api.error.ErrorDeNegocio;
import gt.cunoc.sa.finance.api.error.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * S6: el bus de un gasto de taller, para validar que existe y que es de la sucursal del admin. El
 * circuito va por fuera del reintento (circuitBreakerAspectOrder en application.yml): ve una
 * operación por llamada, con sus reintentos adentro.
 */
@Component
public class FleetClient {

    private static final Logger log = LoggerFactory.getLogger(FleetClient.class);
    private static final String SERVICIO = "fleet";

    private final RestClient http;
    private final ErroresRemotos errores;

    public FleetClient(RestClient.Builder builder, ClientHttpRequestFactory fabricaRemota, ErroresRemotos errores,
            @Value("${clientes.fleet.url}") String url) {
        this.http = errores.restClient(builder, fabricaRemota, url, SERVICIO);
        this.errores = errores;
    }

    /** Solo lee: reintentarlo es seguro. */
    @CircuitBreaker(name = "fleet", fallbackMethod = "busNoDisponible")
    @Retry(name = "fleet")
    public BusRemoto bus(UUID busId) {
        BusRemoto bus = errores.llamar(SERVICIO, () -> http.get().uri("/internal/buses/{id}", busId)
                .retrieve().body(BusRemoto.class));
        if (bus == null || bus.sucursalId() == null || bus.placa() == null) {
            throw new ServicioNoDisponibleException("fleet respondió un bus incompleto", null);
        }
        return bus;
    }

    /**
     * Resilience4j llama al fallback ante cualquier excepción, también las que el circuito ignora:
     * el 4xx de fleet se devuelve tal cual, o un "el bus no existe" saldría como 503.
     */
    @SuppressWarnings("unused")
    private BusRemoto busNoDisponible(UUID busId, Throwable causa) {
        if (causa instanceof ErrorDeNegocio | causa instanceof ServicioNoDisponibleException) {
            throw (RuntimeException) causa;
        }
        if (causa instanceof CallNotPermittedException) {
            log.warn("Circuito de fleet abierto: no se consulta el bus {}", busId);
            throw new ServicioNoDisponibleException("Circuito de fleet abierto", causa);
        }
        throw new ServicioNoDisponibleException("fleet falló", causa);
    }
}
