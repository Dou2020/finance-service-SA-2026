package gt.cunoc.sa.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoría del catálogo de gastos de taller: una nueva no toca código. Con gastos no se borra
 * (el ON DELETE RESTRICT de linea_gasto lo impide), solo se desactiva; desactivada ya no se ofrece
 * para gastos nuevos, pero los que la usan siguen en los reportes.
 */
@Entity
@Table(name = "categoria_gasto")
public class CategoriaGastoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private boolean activa;

    protected CategoriaGastoEntity() {
    }

    public static CategoriaGastoEntity nueva(String nombre) {
        CategoriaGastoEntity c = new CategoriaGastoEntity();
        c.nombre = normalizar(nombre);
        c.activa = true;
        return c;
    }

    /** Sin espacios de más: "Cambio  de aceite " y "Cambio de aceite" son la misma categoría. */
    public static String normalizar(String nombre) {
        return nombre.strip().replaceAll("\\s+", " ");
    }

    public boolean renombrar(String nuevo) {
        String normalizado = normalizar(nuevo);
        if (normalizado.equals(nombre)) {
            return false;
        }
        nombre = normalizado;
        return true;
    }

    public boolean cambiarEstado(boolean activa) {
        if (this.activa == activa) {
            return false;
        }
        this.activa = activa;
        return true;
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActiva() {
        return activa;
    }
}
