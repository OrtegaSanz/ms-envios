package com.nttdata.envios.model;

/**
 * Configuracion activa del servicio. (Resuelto)
 * tokenConfigurado indica si llego el token del courier, sin revelar su valor.
 */
public record ConfigResponse(String moneda,
                             double tarifaBase,
                             double costoPorKg,
                             double recargoExpress,
                             double envioGratisDesde,
                             double pesoMaximoKg,
                             boolean tokenConfigurado) {
}
