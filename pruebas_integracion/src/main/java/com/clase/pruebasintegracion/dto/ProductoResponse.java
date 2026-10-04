package com.clase.pruebasintegracion.dto;

import java.math.BigDecimal;

public record ProductoResponse(Long id, String sku, String nombre, BigDecimal precio, Integer stock) {}