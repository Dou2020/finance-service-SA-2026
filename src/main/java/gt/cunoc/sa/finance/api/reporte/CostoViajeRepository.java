package gt.cunoc.sa.finance.api.reporte;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.CostoViajeEntity;

/** Por el id del viaje en trip, como texto (V3). */
public interface CostoViajeRepository extends JpaRepository<CostoViajeEntity, String> {

    @Query("""
            select c from CostoViajeEntity c
            where (:sucursalId is null or c.sucursalId = :sucursalId)
              and c.fecha between :desde and :hasta
            order by c.fecha, c.registradoEn""")
    List<CostoViajeEntity> delPeriodo(UUID sucursalId, LocalDate desde, LocalDate hasta);
}
