package gt.cunoc.sa.finance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * PostgreSQL y RabbitMQ reales (las mismas imágenes que el compose): Flyway, el
 * ddl-auto=validate, las restricciones que arbitran el libro (una fila por venta o alquiler) y la
 * DLQ se prueban contra los motores de producción, no contra sustitutos en memoria. fleet (S6) es
 * un WireMock: cada prueba simula sus buses con ids propios.
 *
 * Las pruebas que usan @PruebaDeIntegracion comparten el contexto de Spring y, con él, un solo
 * juego de contenedores y un solo WireMock.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ Escenario.class, Publicador.class })
public class ContenedoresDePrueba {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4-management-alpine");
    }

    @Bean(destroyMethod = "stop")
    WireMockServer fleet() {
        WireMockServer servidor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        servidor.start();
        return servidor;
    }

    /** FleetClient lee clientes.fleet.url al crearse: aquí apunta al WireMock de arriba. */
    @Bean
    DynamicPropertyRegistrar urlDeFleet(WireMockServer fleet) {
        return registro -> registro.add("clientes.fleet.url", fleet::baseUrl);
    }
}
