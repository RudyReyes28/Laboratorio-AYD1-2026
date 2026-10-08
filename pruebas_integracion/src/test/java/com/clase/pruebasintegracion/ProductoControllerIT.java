package com.clase.pruebasintegracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
class ProductoControllerIT extends BaseIT {

    @Test
    void crearProducto_debeGuardarloEnBD() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"sku":"LAP-001","nombre":"Laptop","precio":500.00,"stock":10}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("LAP-001"));

        assertThat(productoRepository.findBySku("LAP-001")).isPresent();
    }

    @Test
    void crearProducto_conSkuDuplicado_debeRetornar409() throws Exception {
        guardarProducto("LAP-001", "500.00", 10);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"sku":"LAP-001","nombre":"Otra","precio":300.00,"stock":5}
                    """))
                .andExpect(status().isConflict());

        assertThat(productoRepository.count()).isEqualTo(1);
    }

    @Test
    void crearProducto_conPrecioNegativo_debeRetornar400() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"sku":"LAP-002","nombre":"Laptop","precio":-5,"stock":10}
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtenerProducto_inexistente_debeRetornar404() throws Exception {
        mockMvc.perform(get("/api/productos/9999"))
                .andExpect(status().isNotFound());
    }
}