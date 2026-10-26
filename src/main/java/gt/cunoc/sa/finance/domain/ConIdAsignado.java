package gt.cunoc.sa.finance.domain;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Las entidades con id asignado en el código no se pueden distinguir de una existente por el
 * id: Spring Data haría merge() en vez de persist(), con un SELECT de más, y Hibernate 6.6
 * trata como desactualizada (OptimisticLockException) a una entidad con @Version que no está
 * en la base. Este marcador le dice a save() cuándo es nueva.
 *
 * Genérico en el tipo del id (en identity y customer es solo UUID): el gasto de taller usa un UUID
 * propio, y el costo de un viaje, el id de trip como texto (V3).
 */
@MappedSuperclass
public abstract class ConIdAsignado<ID> implements Persistable<ID> {

    @Transient
    private boolean nueva = true;

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarPersistida() {
        nueva = false;
    }
}
