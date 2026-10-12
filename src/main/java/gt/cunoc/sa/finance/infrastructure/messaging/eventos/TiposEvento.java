package gt.cunoc.sa.finance.infrastructure.messaging.eventos;

/** Routing keys (dominio.agregado.evento) de lo que finance consume. No publica nada. */
public final class TiposEvento {

    public static final String SUCURSAL_GUARDADA = "identity.sucursal.guardada";               // E1 (consume)
    public static final String CONFIGURACION_ACTUALIZADA = "identity.configuracion.actualizada"; // E2 (consume)
    public static final String VIAJE_FINALIZADO = "trip.viaje.finalizado";                     // E9 (consume)
    public static final String BOLETOS_VENDIDOS = "sales.boletos.vendidos";                    // E11 (consume)
    public static final String BOLETOS_ANULADOS = "sales.boletos.anulados";                    // E12 (consume)
    public static final String ALQUILER_PAGADO = "customer.alquiler.pagado";                   // E14 (consume)
    public static final String ALQUILER_CANCELADO = "customer.alquiler.cancelado";             // E16 (consume)

    private TiposEvento() {
    }
}
