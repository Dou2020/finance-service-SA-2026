package gt.cunoc.sa.finance.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convierte el payload del sobre al record del evento. Un payload que no se puede leer nunca se
 * va a poder leer: va directo a la DLQ, sin gastar reintentos. Los campos que el record no tiene
 * se ignoran (Spring Boot configura el ObjectMapper así): el productor puede agregar campos sin
 * romper a este consumidor.
 */
public final class Payloads {

    /**
     * El largo de las columnas de ids de fleet, trip y sales (V3). Uno más largo no cabe: sin esta
     * validación fallaría al insertar, se reintentaría cuatro veces y terminaría igual en la DLQ.
     */
    public static final int LARGO_ID = 64;

    private Payloads() {
    }

    public static <T> T leer(ObjectMapper json, Sobre sobre, Class<T> tipo) {
        try {
            return json.treeToValue(sobre.payload(), tipo);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new EventoNoProcesableException("Payload de " + sobre.type() + " ilegible: " + e.getMessage(), e);
        }
    }

    /** Un dato obligatorio que falta: tampoco se arregla reintentando. */
    public static void exigir(boolean condicion, Sobre sobre, String falta) {
        if (!condicion) {
            throw new EventoNoProcesableException(sobre.type() + " sin " + falta);
        }
    }

    /**
     * Un id de fleet, trip o sales: texto no vacío de hasta LARGO_ID caracteres. Se guarda tal cual
     * llega, sin recortarlo ni pasarlo a minúsculas: el productor manda siempre el mismo texto para
     * el mismo agregado, y normalizar aquí haría que dos ids distintos chocaran.
     */
    public static void exigirId(String id, Sobre sobre, String campo) {
        exigir(id != null && !id.isBlank() && id.length() <= LARGO_ID, sobre,
                campo + " válido (texto no vacío de hasta " + LARGO_ID + " caracteres)");
    }
}
