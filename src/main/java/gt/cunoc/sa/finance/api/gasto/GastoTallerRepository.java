package gt.cunoc.sa.finance.api.gasto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.GastoTallerEntity;

public interface GastoTallerRepository extends JpaRepository<GastoTallerEntity, UUID>,
        JpaSpecificationExecutor<GastoTallerEntity> {

    /** El detalle con sus líneas y categorías en una consulta. */
    @Query("""
            select distinct g from GastoTallerEntity g
            left join fetch g.lineas l
            left join fetch l.categoria
            where g.id = :id""")
    Optional<GastoTallerEntity> conLineas(UUID id);

    /** Los encabezados del período, para el reporte de gastos y el de ganancias. */
    @Query("""
            select g from GastoTallerEntity g
            where (:sucursalId is null or g.sucursalId = :sucursalId)
              and g.fechaServicio between :desde and :hasta
            order by g.fechaServicio, g.creadoEn""")
    List<GastoTallerEntity> delPeriodo(UUID sucursalId, LocalDate desde, LocalDate hasta);
}
