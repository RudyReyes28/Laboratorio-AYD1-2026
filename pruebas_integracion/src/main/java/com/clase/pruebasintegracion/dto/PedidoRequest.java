package com.clase.pruebasintegracion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PedidoRequest(@NotNull Long productoId, @NotNull @Positive Integer cantidad) {}