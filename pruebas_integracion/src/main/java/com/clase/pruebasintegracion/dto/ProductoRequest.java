package com.clase.pruebasintegracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank String sku,
        @NotBlank String nombre,
        @NotNull @Positive BigDecimal precio,
        @NotNull @PositiveOrZero Integer stock) {}