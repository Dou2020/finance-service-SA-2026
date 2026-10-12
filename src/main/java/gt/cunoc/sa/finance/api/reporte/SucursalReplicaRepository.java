package gt.cunoc.sa.finance.api.reporte;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.finance.domain.SucursalReplicaEntity;

public interface SucursalReplicaRepository extends JpaRepository<SucursalReplicaEntity, UUID> {

    /**
     * Inserta la sucursal o la actualiza si la versión que llega es mayor. La comparación va en la
     * misma sentencia: leer, comparar y guardar dejaría que dos E1 se pisaran.
     *
     * @return 1 si se aplicó, 0 si la réplica ya tenía una versión igual o más nueva
     */
    @Modifying
    @Query(value = """
            insert into sucursal_replica (id, nombre, activa, version)
            values (:id, :nombre, :activa, :version)
            on conflict (id) do update
                set nombre = excluded.nombre, activa = excluded.activa, version = excluded.version
                where sucursal_replica.version < excluded.version""", nativeQuery = true)
    int guardar(UUID id, String nombre, boolean activa, long version);
}
