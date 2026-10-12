package gt.cunoc.sa.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sin UserDetailsServiceAutoConfiguration: el servicio no autentica con usuario y
 * contraseña de Spring Security (la identidad llega en los X-User-* del gateway), y
 * esa configuración solo crearía un usuario en memoria con contraseña generada.
 *
 * Sin EnableScheduling: finance no publica eventos (no hay relay de outbox) ni tiene jobs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
