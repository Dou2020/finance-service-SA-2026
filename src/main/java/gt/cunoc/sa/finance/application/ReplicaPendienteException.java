package gt.cunoc.sa.finance.application;

import gt.cunoc.sa.finance.infrastructure.messaging.EventoNoProcesableException;

/**
 * El evento necesita algo que llega por otro evento y finance todavía no tiene: la sucursal (E1) de
 * un ingreso o un costo, o la tarifa de depreciación (E2) vigente a la fecha de llegada de un viaje.
 *
 * Va directo a la DLQ, sin reintentos: el listener procesa la cola de a un mensaje, así que el E1 o
 * el E2 que faltan, si vienen detrás en la misma cola, no se aplican mientras este se reintenta. En la
 * práctica falta porque no se configuró (no hay tarifa) o porque el E1 se publicó antes de que existiera
 * la cola de finance. Se arregla eso y se reprocesa con POST /internal/dlq/reprocesar: el inbox no
 * registró el intento fallido, así que el evento se aplica completo.
 */
public class ReplicaPendienteException extends EventoNoProcesableException {

    public ReplicaPendienteException(String mensaje) {
        super(mensaje);
    }
}
