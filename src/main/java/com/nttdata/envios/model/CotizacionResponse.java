package com.nttdata.envios.model;

/** Resultado de una cotizacion. (Resuelto) */
public record CotizacionResponse(double costo, String moneda, boolean gratis, boolean express) {
}
