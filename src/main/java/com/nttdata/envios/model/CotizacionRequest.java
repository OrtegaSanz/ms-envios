package com.nttdata.envios.model;

/** Peticion de cotizacion. (Resuelto) */
public record CotizacionRequest(double pesoKg, double montoCompra, boolean express) {
}
