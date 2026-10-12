package gt.cunoc.sa.finance.api.interno;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.finance.infrastructure.messaging.ReprocesadorDlq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Rutas para el operador. El gateway no las expone: solo se llega por la red interna de Compose
 * (README, "Premisa: solo el gateway llega a los servicios"). finance no recibe llamadas de otros
 * servicios: todo lo que necesita le llega por eventos.
 */
@RestController
@RequestMapping("/internal")
@SecurityRequirements
@Tag(name = "Interno", description = "Solo red interna; el gateway no expone /internal/**")
public class InternoController {

    private final ReprocesadorDlq reprocesador;

    public InternoController(ReprocesadorDlq reprocesador) {
        this.reprocesador = reprocesador;
    }

    @PostMapping("/dlq/reprocesar")
    @Operation(summary = "Reencola los mensajes de finance.events.dlq")
    public Map<String, Integer> reprocesarDlq() {
        return Map.of("reprocesados", reprocesador.reprocesar());
    }
}
