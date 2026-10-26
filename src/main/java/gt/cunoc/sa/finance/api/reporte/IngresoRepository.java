package gt.cunoc.sa.finance.api.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.IngresoEntity;

/**
 * El libro de ingresos. Las escrituras son una sola sentencia cada una: la UQ (tipo, referencia_id)
 * decide entre el ingreso y su reverso, lleguen en el orden que lleguen.
 */
public interface IngresoRepository extends JpaRepository<IngresoEntity, Long> {

    /** Los que cuentan en los reportes: los reversados no, aunque su monto siga en el libro. */
    @Query("""
            select i from IngresoEntity i
            where i.reversado = false
              and (:sucursalId is null or i.sucursalId = :sucursalId)
              and i.fecha between :desde and :hasta
            order by i.fecha, i.registradoEn""")
    List<IngresoEntity> vigentes(UUID sucursalId, LocalDate desde, LocalDate hasta);

    /**
     * El ingreso de una venta o un alquiler. Si ya existe (un E11 o E14 repetido) o ya está su lápida
     * reversada (el E12 o E16 llegó antes), no se toca.
     *
     * @return 1 si se registró
     */
    @Modifying
    @Query(value = """
            insert into ingreso (tipo, referencia_id, sucursal_id, monto, fecha, detalle)
            values (:tipo, :referenciaId, :sucursalId, :monto, :fecha, :detalle)
            on conflict (tipo, referencia_id) do nothing""", nativeQuery = true)
    int registrar(String tipo, String referenciaId, UUID sucursalId, BigDecimal monto, LocalDate fecha, String detalle);

    /**
     * Reversa el ingreso, o deja la lápida si todavía no llegó (o nunca va a llegar, como la venta que
     * sales anula antes de confirmar): una fila ya reversada con los datos del reverso, para que un
     * E11 o E14 tardío choque con ella y no cuente.
     *
     * @return 1 si cambió algo, 0 si ya estaba reversado
     */
    @Modifying
    @Query(value = """
            insert into ingreso (tipo, referencia_id, sucursal_id, monto, fecha, detalle, reversado, reversado_en)
            values (:tipo, :referenciaId, :sucursalId, :monto, :fecha, :detalle, true, now())
            on conflict (tipo, referencia_id) do update set reversado = true, reversado_en = now()
                where not ingreso.reversado""", nativeQuery = true)
    int reversar(String tipo, String referenciaId, UUID sucursalId, BigDecimal monto, LocalDate fecha, String detalle);
}
