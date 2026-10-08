package com.clase.pruebasintegracion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clase.pruebasintegracion.client.PagoClient;
import com.clase.pruebasintegracion.client.RespuestaPago;
import com.clase.pruebasintegracion.model.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
class PedidoControllerIT extends BaseIT {

    @MockitoBean
    PagoClient pagoClient;

    private static final String PEDIDO = """
        {"productoId":%d,"cantidad":%d}
        """;

    @Test
    void crearPedido_conPagoAprobado_descuentaStockYGuardaPedido() throws Exception {
        Producto p = guardarProducto("LAP-001", "500.00", 10);
        when(pagoClient.cobrar(any())).thenReturn(new RespuestaPago(true));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO.formatted(p.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.total").value(1000.0));

        assertThat(productoRepository.findById(p.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(pedidoRepository.count()).isEqualTo(1);
        verify(pagoClient).cobrar(any());
    }

    @Test
    void crearPedido_conPagoRechazado_debeRetornar402YNoCambiarStock() throws Exception {
        Producto p = guardarProducto("LAP-001", "500.00", 10);
        when(pagoClient.cobrar(any())).thenReturn(new RespuestaPago(false));

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO.formatted(p.getId(), 2)))
                .andExpect(status().isPaymentRequired());

        assertThat(productoRepository.findById(p.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(pedidoRepository.count()).isZero();
    }

    @Test
    void crearPedido_conStockInsuficiente_debeRetornar409YNoCobrar() throws Exception {
        Producto p = guardarProducto("LAP-001", "500.00", 10);

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO.formatted(p.getId(), 11)))
                .andExpect(status().isConflict());

        verify(pagoClient, never()).cobrar(any());
        assertThat(pedidoRepository.count()).isZero();
    }

    @Test
    void crearPedido_conProductoInexistente_debeRetornar404() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PEDIDO.formatted(9999, 1)))
                .andExpect(status().isNotFound());
    }
}