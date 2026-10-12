package gt.cunoc.sa.finance.api.error;

/**
 * fleet no respondió a tiempo, respondió 5xx o su circuito está abierto (S6, validar el bus de
 * un gasto de taller). Sale como 503 y la transacción se revierte: el gasto no se guarda sin la
 * validación.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
