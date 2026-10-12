package gt.cunoc.sa.finance.api.reporte;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import gt.cunoc.sa.finance.domain.LineaGastoEntity;

/** Solo lectura, para los reportes de taller; las líneas se escriben con su gasto. */
public interface LineaGastoRepository extends Repository<LineaGastoEntity, Long> {

    @Query("""
            select new gt.cunoc.sa.finance.api.reporte.LineaDeTaller(g.id, g.busId, g.placaBus, g.sucursalId,
                   g.fechaServicio, c.id, c.nombre, l.descripcion, l.tipo, l.monto)
            from LineaGastoEntity l join l.gasto g join l.categoria c
            where (:sucursalId is null or g.sucursalId = :sucursalId)
              and (:busId is null or g.busId = :busId)
              and g.fechaServicio between :desde and :hasta
            order by g.fechaServicio, g.creadoEn, l.id""")
    List<LineaDeTaller> delPeriodo(UUID sucursalId, UUID busId, LocalDate desde, LocalDate hasta);
}
