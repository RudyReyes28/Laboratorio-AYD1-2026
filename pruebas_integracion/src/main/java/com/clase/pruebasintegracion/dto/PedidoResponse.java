package com.clase.pruebasintegracion.dto;

import java.math.BigDecimal;

public record PedidoResponse(Long id, Long productoId, Integer cantidad, BigDecimal total, String estado) {}