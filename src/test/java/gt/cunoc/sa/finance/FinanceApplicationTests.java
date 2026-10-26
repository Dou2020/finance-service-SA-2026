package gt.cunoc.sa.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Si el contexto levanta, Flyway aplicó las migraciones y Hibernate validó que las entidades
 * coinciden con el esquema (ddl-auto=validate falla al arrancar si no).
 */
@PruebaDeIntegracion
class FinanceApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaLasMigraciones() {
        List<String> tablas = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tablas).contains("sucursal_replica", "tarifa_depreciacion", "categoria_gasto", "gasto_taller",
                "linea_gasto", "costo_viaje", "ingreso", "processed_event");
        // finance solo consume: sin outbox (DIAGRAMAS.md §7.7).
        assertThat(tablas).doesNotContain("outbox_event");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(3);
    }

    /** V3: los ids de fleet, trip y sales son texto; los de identity siguen siendo UUID. */
    @Test
    void losIdsDeLosImportadosSonTexto() {
        List<String> texto = jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'public' and data_type = 'character varying' and character_maximum_length = 64""",
                String.class);
        assertThat(texto).contains("gasto_taller.bus_id", "costo_viaje.viaje_id", "costo_viaje.bus_id",
                "costo_viaje.chofer_id", "ingreso.referencia_id");
        assertThat(jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_name = 'costo_viaje' and column_name = 'sucursal_id'""", String.class)).isEqualTo("uuid");
    }

    /** V2: las categorías del enunciado ya están en el catálogo. */
    @Test
    void traeLasCategoriasIniciales() {
        assertThat(jdbc.queryForList("select nombre from categoria_gasto", String.class))
                .contains("Llantas", "Motor", "Frenos", "Suspensión", "Cambio de aceite", "Eléctrico", "Carrocería",
                        "Otros");
    }
}
