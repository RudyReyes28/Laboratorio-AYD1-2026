package com.clase.pruebasintegracion.client;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PagoClientSimulado implements PagoClient {
    @Override
    public RespuestaPago cobrar(BigDecimal monto) {
        // Simula una pasarela: rechaza montos mayores a 1000
        return new RespuestaPago(monto.compareTo(new BigDecimal("1000")) <= 0);
    }
}