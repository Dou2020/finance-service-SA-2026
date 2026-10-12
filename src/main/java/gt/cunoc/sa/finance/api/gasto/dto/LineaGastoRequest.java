package gt.cunoc.sa.finance.api.gasto.dto;

import java.math.BigDecimal;

import gt.cunoc.sa.finance.domain.TipoLinea;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LineaGastoRequest(
        @NotNull Integer categoriaId,
        @NotBlank @Size(max = 200) String descripcion,
        @NotNull TipoLinea tipo,
        @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "debe tener a lo sumo 2 decimales") BigDecimal monto) {
}
