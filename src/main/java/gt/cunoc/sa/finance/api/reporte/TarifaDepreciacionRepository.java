package gt.cunoc.sa.finance.api.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.TarifaDepreciacionEntity;

public interface TarifaDepreciacionRepository extends JpaRepository<TarifaDepreciacionEntity, Long> {

    /** La versión vigente a la fecha: la de vigente_desde más reciente que no la pasa. */
    Optional<TarifaDepreciacionEntity> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);

    /** Con el id de identity: un ConfiguracionActualizada repetido no duplica la versión. */
    @Modifying
    @Query(value = """
            insert into tarifa_depreciacion (id, monto_por_km, vigente_desde)
            values (:id, :montoPorKm, :vigenteDesde)
            on conflict do nothing""", nativeQuery = true)
    int insertar(long id, BigDecimal montoPorKm, LocalDate vigenteDesde);
}
