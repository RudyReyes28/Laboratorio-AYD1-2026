package com.clase.pruebasintegracion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "pedidos")
@Getter @Setter @NoArgsConstructor
public class Pedido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "producto_id")
    private Producto producto;
    private Integer cantidad;
    private BigDecimal total;
    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;
    @Column(name = "creado_en")
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Pedido(Producto producto, Integer cantidad, BigDecimal total, EstadoPedido estado) {
        this.producto = producto; this.cantidad = cantidad;
        this.total = total; this.estado = estado;
    }
}