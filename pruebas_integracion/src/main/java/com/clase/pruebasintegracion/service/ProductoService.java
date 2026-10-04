package com.clase.pruebasintegracion.service;

import com.clase.pruebasintegracion.dto.ProductoRequest;
import com.clase.pruebasintegracion.dto.ProductoResponse;
import com.clase.pruebasintegracion.exception.RecursoNoEncontradoException;
import com.clase.pruebasintegracion.model.Producto;
import com.clase.pruebasintegracion.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {
    private final ProductoRepository repo;

    public ProductoResponse crear(ProductoRequest r) {
        // Sin validar el SKU a mano: la restricción UNIQUE de la BD lanza la excepción
        Producto p = repo.save(new Producto(r.sku(), r.nombre(), r.precio(), r.stock()));
        return aDto(p);
    }
    public ProductoResponse obtener(Long id) {
        return aDto(repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no existe")));
    }
    public List<ProductoResponse> listar() {
        return repo.findAll().stream().map(this::aDto).toList();
    }
    private ProductoResponse aDto(Producto p) {
        return new ProductoResponse(p.getId(), p.getSku(), p.getNombre(), p.getPrecio(), p.getStock());
    }
}