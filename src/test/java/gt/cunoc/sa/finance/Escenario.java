package gt.cunoc.sa.finance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.github.tomakehurst.wiremock.WireMockServer;

import gt.cunoc.sa.finance.domain.Rol;
import gt.cunoc.sa.finance.infrastructure.security.UsuarioActual;

/**
 * Datos de prueba escritos directo en la base (sin eventos) y buses simulados en el WireMock de
 * fleet: cada prueba arma lo suyo con ids y códigos únicos, porque todas comparten la misma base y
 * el mismo WireMock. Los usuarios no viven aquí, solo llegan en los X-User-* del gateway: las
 * fábricas de UsuarioActual usan ids al azar.
 */
@TestComponent
public class Escenario {

    private static final AtomicInteger CONTADOR = new AtomicInteger(ThreadLocalRandom.current().nextInt(100_000));

    private final JdbcTemplate jdbc;
    private final WireMockServer fleet;

    public Escenario(JdbcTemplate jdbc, WireMockServer fleet) {
        this.jdbc = jdbc;
        this.fleet = fleet;
    }

    /** Un bus que fleet (S6) conoce, de esa sucursal. */
    public Bus bus(UUID sucursal) {
        Bus bus = new Bus(UUID.randomUUID(), "P" + (100 + CONTADOR.incrementAndGet() % 900) + "BCD");
        fleet.stubFor(get(urlEqualTo("/internal/buses/" + bus.id())).willReturn(okJson("""
                {"id": "%s", "placa": "%s", "sucursalId": "%s", "estado": "DISPONIBLE"}"""
                .formatted(bus.id(), bus.placa(), sucursal))));
        return bus;
    }

    /** fleet responde a ese bus como lo haría su ManejadorErrores. */
    public void fleetResponde(UUID busId, int estado, String detalle) {
        fleet.stubFor(get(urlEqualTo("/internal/buses/" + busId)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\": %d, \"detail\": \"%s\"}".formatted(estado, detalle))));
    }

    /** fleet tarda más que el timeout de respuesta (2 s) con ese bus. */
    public void fleetTarda(UUID busId, int milisegundos) {
        fleet.stubFor(get(urlEqualTo("/internal/buses/" + busId)).willReturn(okJson("{}")
                .withFixedDelay(milisegundos)));
    }

    /** Cuántas veces se llamó a fleet por ese bus. */
    public int llamadasAFleet(UUID busId) {
        return fleet.countRequestsMatching(getRequestedFor(urlEqualTo("/internal/buses/" + busId)).build())
                .getCount();
    }

    public record Bus(UUID id, String placa) {
    }

    /** Una sucursal ya replicada (como si hubiera llegado su E1), con un nombre único. */
    public UUID sucursal() {
        return sucursal(codigo("Sucursal "));
    }

    /** Con el nombre dado: los reportes ordenan por nombre. */
    public UUID sucursal(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into sucursal_replica (id, nombre, activa, version) values (?, ?, true, 1)", id, nombre);
        return id;
    }

    /** Una categoría activa con nombre único. */
    public int categoria() {
        return jdbc.queryForObject("insert into categoria_gasto (nombre) values (?) returning id", Integer.class,
                codigo("Categoría "));
    }

    /** Un gasto de una línea escrito directo en la base, para lo que solo necesita que exista. */
    public UUID gasto(UUID sucursal, int categoria) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into gasto_taller (id, bus_id, placa_bus, sucursal_id, fecha_servicio, total_mano_obra,
                                          total_repuestos, total, registrado_por)
                values (?, ?, 'P123ABC', ?, current_date, 100.00, 0, 100.00, ?)""",
                id, UUID.randomUUID(), sucursal, UUID.randomUUID());
        jdbc.update("""
                insert into linea_gasto (gasto_id, categoria_id, descripcion, tipo, monto)
                values (?, ?, 'Revisión', 'MANO_DE_OBRA', 100.00)""", id, categoria);
        return id;
    }

    public static UsuarioActual sysAdmin() {
        return actual(Rol.SYS_ADMIN, null, null);
    }

    public static UsuarioActual adminDe(UUID sucursal) {
        return actual(Rol.BRANCH_ADMIN, sucursal, null);
    }

    public static UsuarioActual cajeroDe(UUID sucursal) {
        return actual(Rol.CASHIER, sucursal, null);
    }

    public static UsuarioActual cliente() {
        return actual(Rol.CUSTOMER, null, null);
    }

    /** Igual que el JWT de un DRIVER: trae choferId y no sucursal. */
    public static UsuarioActual chofer(UUID choferId) {
        return actual(Rol.DRIVER, null, choferId);
    }

    private static UsuarioActual actual(Rol rol, UUID sucursal, UUID chofer) {
        return new UsuarioActual(UUID.randomUUID(), sucursal, chofer, Set.of(rol.name()));
    }

    /** Los encabezados que escribe el gateway. */
    public static RequestPostProcessor como(UsuarioActual actual) {
        return request -> {
            request.addHeader("X-User-Id", actual.usuarioId().toString());
            request.addHeader("X-User-Roles", String.join(",", actual.roles()));
            if (actual.sucursalId() != null) {
                request.addHeader("X-Sucursal-Id", actual.sucursalId().toString());
            }
            if (actual.choferId() != null) {
                request.addHeader("X-Chofer-Id", actual.choferId().toString());
            }
            return request;
        };
    }

    /** Para llamar casos de uso con @PreAuthorize fuera de un request (pruebas con hilos). */
    public static void autenticar(UsuarioActual actual) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actual, null, actual.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    /** Un código único entre pruebas (nombres de categoría, placas, detalles). */
    public static String codigo(String prefijo) {
        return prefijo + CONTADOR.incrementAndGet() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
    }
}
