package gt.cunoc.sa.finance.api.gasto.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param fechaServicio la del servicio, que elige el usuario; null es hoy (README, "Fechas")
 */
public record GastoTallerRequest(
        @NotNull UUID busId,
        LocalDate fechaServicio,
        @NotEmpty(message = "debe tener al menos una línea") @Size(max = 50)
        List<@Valid @NotNull LineaGastoRequest> lineas) {
}
