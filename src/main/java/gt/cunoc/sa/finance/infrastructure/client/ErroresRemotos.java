package gt.cunoc.sa.finance.infrastructure.client;

import java.io.IOException;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.finance.api.error.ErrorDeNegocio;
import gt.cunoc.sa.finance.api.error.ServicioNoDisponibleException;
import gt.cunoc.sa.finance.infrastructure.web.CorrelationIdFilter;

/**
 * Traduce lo que pasó en el cable a las dos excepciones que entiende finance (modelo: PRACTICA_5,
 * RemoteErrors):
 *
 * | Qué pasó                                  | Excepción                      | Sale como      |
 * |-------------------------------------------|--------------------------------|----------------|
 * | 4xx                                       | ErrorDeNegocio, mismo estado   | el 4xx de fleet, con su detail |
 * | 5xx, sin conexión, timeout                | ServicioNoDisponibleException  | 503            |
 *
 * Un 4xx es la respuesta de un servicio sano ("el bus no existe"): no abre el circuito ni se
 * reintenta (ignoreExceptions en application.yml).
 */
@Component
public class ErroresRemotos {

    private final ObjectMapper json;

    public ErroresRemotos(ObjectMapper json) {
        this.json = json;
    }

    /** RestClient con los timeouts comunes, el X-Correlation-Id del request y el mapeo de estados. */
    public RestClient restClient(RestClient.Builder builder, ClientHttpRequestFactory fabrica, String url,
            String servicio) {
        return builder
                .baseUrl(url)
                .requestFactory(fabrica)
                .requestInterceptor((request, cuerpo, ejecucion) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_CLAVE);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.ENCABEZADO, correlationId);
                    }
                    return ejecucion.execute(request, cuerpo);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, respuesta) -> {
                    throw deEstado(servicio, respuesta);
                })
                .build();
    }

    /** Ejecuta la llamada y traduce los errores de transporte (timeout, sin conexión). */
    public <T> T llamar(String servicio, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            throw new ServicioNoDisponibleException(servicio + " no respondió", e);
        }
    }

    private RuntimeException deEstado(String servicio, ClientHttpResponse respuesta) throws IOException {
        HttpStatusCode estado = respuesta.getStatusCode();
        if (estado.is4xxClientError()) {
            String detalle = detalle(respuesta);
            return ErrorDeNegocio.conEstado(estado,
                    detalle != null ? detalle : servicio + " respondió " + estado.value());
        }
        return new ServicioNoDisponibleException(servicio + " respondió " + estado.value(), null);
    }

    /** El "detail" del ProblemDetail del otro servicio, que ya está en lenguaje claro. */
    private String detalle(ClientHttpResponse respuesta) {
        try {
            JsonNode cuerpo = json.readTree(respuesta.getBody());
            return cuerpo != null && cuerpo.hasNonNull("detail") ? cuerpo.get("detail").asText() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
