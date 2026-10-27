package com.nttdata.envios;

import com.nttdata.envios.model.CotizacionRequest;
import com.nttdata.envios.model.CotizacionResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calcula el costo de un envio con la configuracion activa. */
@Service
public class CotizadorService {

    private final EnviosProperties props;

    public CotizadorService(EnviosProperties props) {
        this.props = props;
    }

    public CotizacionResponse cotizar(CotizacionRequest req) {
        // TODO 10 (RN-04): si el peso es <= 0 o mayor a props.pesoMaximoKg(),
        //                  lanza IllegalArgumentException.

        // TODO 13 (RN-03): si NO es express y la compra es >= props.envioGratisDesde(),
        //                  devuelve costo 0.00 con gratis = true.

        // TODO 11 (RN-01): costo = tarifa base + peso x costo por kg.

        // TODO 12 (RN-02): si es express, aplica el recargo: costo x (1 + recargoExpress).

        // TODO 14 (RN-05): devuelve la respuesta con el costo redondeado a 2 decimales
        //                  usando el metodo redondear(...).

        if (req.pesoKg() <= 0 || req.pesoKg() > props.pesoMaximoKg()) {
            throw new IllegalArgumentException();
        }

        if (!req.express() && req.montoCompra() >= props.envioGratisDesde()) {
            return new CotizacionResponse(0.00, "PEN", true, false);
        }

        double costo = props.tarifaBase() + req.pesoKg() * props.costoPorKg();

        if (req.express()) {
            double costoExpress = redondear(costo * (1 + props.recargoExpress()));
            return new CotizacionResponse(costoExpress, "PEN", false, true);
        }

        return new CotizacionResponse(redondear(costo), "PEN", false, false);
    }

    /** Redondeo a 2 decimales, HALF_UP. (Resuelto) */
    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
