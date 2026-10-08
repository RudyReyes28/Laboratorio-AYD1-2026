package com.clase.pruebasintegracion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.clase.pruebasintegracion.model.Producto;
import com.clase.pruebasintegracion.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;          // sin confirmar
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase; // sin confirmar
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductoRepositoryIT {

    @Autowired
    ProductoRepository repository;

    @Test
    void findBySku_debeRetornarElProducto() {
        repository.save(new Producto("REPO-001", "Mouse", new BigDecimal("20.00"), 5));

        assertThat(repository.findBySku("REPO-001")).isPresent();
    }

    @Test
    void guardarSkuDuplicado_debeFallarPorRestriccionDeLaBD() {
        repository.saveAndFlush(new Producto("REPO-002", "Teclado", new BigDecimal("30.00"), 5));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new Producto("REPO-002", "Otro", new BigDecimal("10.00"), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}