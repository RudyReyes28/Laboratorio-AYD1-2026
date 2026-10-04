package com.clase.pruebasintegracion.service;

import com.clase.pruebasintegracion.client.PagoClient;
import com.clase.pruebasintegracion.dto.PedidoRequest;
import com.clase.pruebasintegracion.dto.PedidoResponse;
import com.clase.pruebasintegracion.exception.ConflictoException;
import com.clase.pruebasintegracion.exception.PagoRechazadoException;
import com.clase.pruebasintegracion.exception.RecursoNoEncontradoException;
import com.clase.pruebasintegracion.model.EstadoPedido;
import com.clase.pruebasintegracion.model.Pedido;
import com.clase.pruebasintegracion.model.Producto;
import com.clase.pruebasintegracion.repository.PedidoRepository;
import com.clase.pruebasintegracion.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PedidoService {
    private final ProductoRepository productos;
    private final PedidoRepository pedidos;
    private final PagoClient pagoClient;

    @Transactional
    public PedidoResponse crear(PedidoRequest r) {
        Producto p = productos.findById(r.productoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no existe"));
        if (p.getStock() < r.cantidad()) throw new ConflictoException("Stock insuficiente");

        BigDecimal total = p.getPrecio().multiply(BigDecimal.valueOf(r.cantidad()));
        p.setStock(p.getStock() - r.cantidad());

        if (!pagoClient.cobrar(total).aprobado())
            throw new PagoRechazadoException("Pago rechazado");   // rollback: el stock no cambia

        return aDto(pedidos.save(new Pedido(p, r.cantidad(), total, EstadoPedido.PAGADO)));
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id) {
        return aDto(pedidos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no existe")));
    }

    private PedidoResponse aDto(Pedido p) {
        return new PedidoResponse(p.getId(), p.getProducto().getId(),
                p.getCantidad(), p.getTotal(), p.getEstado().name());
    }
}