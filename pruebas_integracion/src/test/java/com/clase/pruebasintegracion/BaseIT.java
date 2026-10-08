package com.clase.pruebasintegracion;

import com.clase.pruebasintegracion.model.Producto;
import com.clase.pruebasintegracion.repository.PedidoRepository;
import com.clase.pruebasintegracion.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class BaseIT {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired protected ProductoRepository productoRepository;
    @Autowired protected PedidoRepository pedidoRepository;

    @BeforeEach
    void limpiarBD() {
        pedidoRepository.deleteAll();     // primero pedidos (llave foránea)
        productoRepository.deleteAll();
    }

    protected Producto guardarProducto(String sku, String precio, int stock) {
        return productoRepository.save(new Producto(sku, "Producto " + sku, new BigDecimal(precio), stock));
    }
}