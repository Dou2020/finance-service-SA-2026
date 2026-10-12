package gt.cunoc.sa.finance.api.categoria;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.CategoriaGastoEntity;
import jakarta.persistence.LockModeType;

public interface CategoriaRepository extends JpaRepository<CategoriaGastoEntity, Integer> {

    List<CategoriaGastoEntity> findAllByOrderByNombre();

    List<CategoriaGastoEntity> findByActivaOrderByNombre(boolean activa);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    /**
     * Las categorías de un gasto nuevo con FOR SHARE: un borrado o una desactivación simultánea espera
     * a que el gasto se confirme, y entonces el borrado choca con el RESTRICT. Sin el candado, el
     * gasto podría quedar con una categoría que se desactivó (o se borró) a la mitad.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CategoriaGastoEntity c where c.id in :ids")
    List<CategoriaGastoEntity> bloquearParaUso(Collection<Integer> ids);

    /** Solo para el mensaje: el árbitro es el ON DELETE RESTRICT de linea_gasto. */
    @Query(value = "select exists (select 1 from linea_gasto where categoria_id = :id)", nativeQuery = true)
    boolean tieneGastos(Integer id);
}
