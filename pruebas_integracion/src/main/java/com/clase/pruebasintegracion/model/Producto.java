package com.clase.pruebasintegracion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;
    private String nombre;
    private BigDecimal precio;
    private Integer stock;

    public Producto(String sku, String nombre, BigDecimal precio, Integer stock) {
        this.sku = sku; this.nombre = nombre; this.precio = precio; this.stock = stock;
    }
}