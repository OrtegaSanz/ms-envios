package com.nttdata.envios;

import com.nttdata.envios.model.ConfigResponse;
import com.nttdata.envios.model.CotizacionRequest;
import com.nttdata.envios.model.CotizacionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/envios")
public class EnviosController {

    private final CotizadorService cotizador;
    private final EnviosProperties props;

    /** Llega del Secret como variable de entorno COURIER_API_TOKEN. Nunca se escribe aqui. */
    @Value("${courier.api-token:}")
    String apiToken;

    public EnviosController(CotizadorService cotizador, EnviosProperties props) {
        this.cotizador = cotizador;
        this.props = props;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        // TODO 15 (RN-06): devuelve un ConfigResponse con los 6 valores de props
        //                  y tokenConfigurado = true si apiToken no es nulo ni esta en blanco.
        //                  El valor del token NUNCA se devuelve.
        boolean tokenConfigurado = apiToken != null && !apiToken.isBlank();
        return new ConfigResponse(
                props.moneda(),
                props.tarifaBase(),
                props.costoPorKg(),
                props.recargoExpress(),
                props.envioGratisDesde(),
                props.pesoMaximoKg(),
                tokenConfigurado
        );
    }

    /** RN-04: un peso fuera de rango responde 400. (Resuelto) */
    @PostMapping("/cotizar")
    public ResponseEntity<CotizacionResponse> cotizar(@RequestBody CotizacionRequest request) {
        try {
            return ResponseEntity.ok(cotizador.cotizar(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
