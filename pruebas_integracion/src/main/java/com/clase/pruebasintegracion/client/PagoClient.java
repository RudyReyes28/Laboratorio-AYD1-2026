package com.clase.pruebasintegracion.client;

import java.math.BigDecimal;

public interface PagoClient {
    RespuestaPago cobrar(BigDecimal monto);
}