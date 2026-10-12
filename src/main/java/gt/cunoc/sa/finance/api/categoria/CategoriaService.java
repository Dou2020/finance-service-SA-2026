package gt.cunoc.sa.finance.api.categoria;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.finance.api.categoria.dto.CategoriaResponse;
import gt.cunoc.sa.finance.api.error.ErrorDeNegocio;
import gt.cunoc.sa.finance.domain.CategoriaGastoEntity;

/**
 * Catálogo de categorías de gasto (README, "Taller y categorías"). Lo administra el SYS_ADMIN; los
 * BRANCH_ADMIN lo leen para registrar gastos. Las verificaciones previas solo dan un mensaje mejor:
 * el índice único sobre lower(nombre) y el RESTRICT de linea_gasto son los árbitros.
 */
@Service
public class CategoriaService {

    private static final String REPETIDA = "Ya existe una categoría con ese nombre";

    private final CategoriaRepository categorias;

    public CategoriaService(CategoriaRepository categorias) {
        this.categorias = categorias;
    }

    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(Boolean activa) {
        List<CategoriaGastoEntity> lista = activa == null
                ? categorias.findAllByOrderByNombre()
                : categorias.findByActivaOrderByNombre(activa);
        return lista.stream().map(CategoriaResponse::de).toList();
    }

    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Transactional
    public CategoriaResponse crear(String nombre) {
        CategoriaGastoEntity categoria = CategoriaGastoEntity.nueva(nombre);
        if (categorias.existsByNombreIgnoreCase(categoria.getNombre())) {
            throw ErrorDeNegocio.conflicto(REPETIDA);
        }
        return CategoriaResponse.de(categorias.saveAndFlush(categoria));
    }

    /** Renombrar cambia el nombre también en los reportes de gastos ya registrados: es el mismo catálogo. */
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Transactional
    public CategoriaResponse renombrar(int id, String nombre) {
        CategoriaGastoEntity categoria = buscar(id);
        if (categorias.existsByNombreIgnoreCaseAndIdNot(CategoriaGastoEntity.normalizar(nombre), id)) {
            throw ErrorDeNegocio.conflicto(REPETIDA);
        }
        if (categoria.renombrar(nombre)) {
            categorias.flush();
        }
        return CategoriaResponse.de(categoria);
    }

    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Transactional
    public CategoriaResponse cambiarEstado(int id, boolean activa) {
        CategoriaGastoEntity categoria = buscar(id);
        categoria.cambiarEstado(activa);
        return CategoriaResponse.de(categoria);
    }

    /**
     * Si un gasto la toma entre la verificación y el borrado, el RESTRICT rechaza el DELETE y
     * ManejadorErrores da el mismo 409.
     */
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Transactional
    public void eliminar(int id) {
        CategoriaGastoEntity categoria = buscar(id);
        if (categorias.tieneGastos(id)) {
            throw ErrorDeNegocio.conflicto("La categoría tiene gastos registrados: solo se puede desactivar");
        }
        categorias.delete(categoria);
        categorias.flush();
    }

    private CategoriaGastoEntity buscar(int id) {
        return categorias.findById(id).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe esa categoría"));
    }
}
