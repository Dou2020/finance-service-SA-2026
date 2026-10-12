package gt.cunoc.sa.finance.api.categoria.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoCategoriaRequest(@NotNull Boolean activa) {
}
