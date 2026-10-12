package gt.cunoc.sa.finance.application;

import java.time.LocalDate;

import gt.cunoc.sa.finance.api.error.ErrorDeNegocio;

/**
 * El intervalo de una consulta o un reporte, sobre la fecha de negocio (la que eligió el usuario).
 * Los dos extremos son opcionales e inclusivos: sin intervalo se toman todos los registros
 * (enunciado, "Reportes").
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    /**
     * Los extremos que se consultan cuando no se dio uno. Las consultas usan "between" en vez de
     * "(:desde is null or …)": PostgreSQL no puede deducir el tipo de un parámetro de fecha nulo.
     */
    private static final LocalDate PRIMER_DIA = LocalDate.of(1, 1, 1);
    private static final LocalDate ULTIMO_DIA = LocalDate.of(9999, 12, 31);

    public Periodo {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ErrorDeNegocio.solicitudInvalida("La fecha inicial no puede ser posterior a la final");
        }
    }

    public LocalDate inicio() {
        return desde == null ? PRIMER_DIA : desde;
    }

    public LocalDate fin() {
        return hasta == null ? ULTIMO_DIA : hasta;
    }
}
