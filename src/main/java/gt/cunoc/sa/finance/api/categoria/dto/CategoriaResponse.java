package gt.cunoc.sa.finance.api.categoria.dto;

import gt.cunoc.sa.finance.domain.CategoriaGastoEntity;

public record CategoriaResponse(int id, String nombre, boolean activa) {

    public static CategoriaResponse de(CategoriaGastoEntity c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.isActiva());
    }
}
