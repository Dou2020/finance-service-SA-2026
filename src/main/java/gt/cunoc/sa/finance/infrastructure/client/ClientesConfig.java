package gt.cunoc.sa.finance.infrastructure.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Timeouts de las llamadas a otros servicios (modelo: PRACTICA_5, ClientConfig). Sin ellos, fleet
 * colgado retendría el hilo del request indefinidamente y el circuit breaker nunca vería un fallo.
 * El HttpClient del JDK distingue por tipo el timeout de conexión del de respuesta; aquí da igual
 * (S6 solo lee), pero es la misma fábrica que usan los demás servicios.
 */
@Configuration
public class ClientesConfig {

    @Bean
    JdkClientHttpRequestFactory fabricaRemota(@Value("${clientes.conexion:1s}") Duration conexion,
            @Value("${clientes.respuesta:2s}") Duration respuesta) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(conexion)
                // Sin esto intenta un upgrade a HTTP/2 (h2c) en cada request a Tomcat.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(respuesta);
        return fabrica;
    }
}
