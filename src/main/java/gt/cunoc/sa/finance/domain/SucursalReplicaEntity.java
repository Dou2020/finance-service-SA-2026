package gt.cunoc.sa.finance.domain;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réplica de una sucursal de identity (E1): agrupa los reportes por nombre sin preguntarle a
 * identity. Solo se escribe con el upsert de ReplicaSucursales, que compara la versión en la misma
 * sentencia; por eso aquí es de solo lectura.
 */
@Entity
@Immutable
@Table(name = "sucursal_replica")
public class SucursalReplicaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean activa;

    @Column(nullable = false)
    private long version;

    protected SucursalReplicaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActiva() {
        return activa;
    }

    public long getVersion() {
        return version;
    }
}
