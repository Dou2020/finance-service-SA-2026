package gt.cunoc.sa.finance.api.gasto.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param busId         el id del bus en fleet, como texto: fleet numera con bigserial ("7"). Un
 *                      número JSON también entra (Jackson lo lee como su texto). 64 es el largo de
 *                      la columna (V3)
 * @param fechaServicio la del servicio, que elige el usuario; null es hoy (README, "Fechas")
 */
public record GastoTallerRequest(
        @NotBlank @Size(max = 64) String busId,
        LocalDate fechaServicio,
        @NotEmpty(message = "debe tener al menos una línea") @Size(max = 50)
        List<@Valid @NotNull LineaGastoRequest> lineas) {
}
