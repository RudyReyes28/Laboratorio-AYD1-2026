package com.clase.pruebasintegracion.controller;

import com.clase.pruebasintegracion.dto.ProductoRequest;
import com.clase.pruebasintegracion.dto.ProductoResponse;
import com.clase.pruebasintegracion.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {
    private final ProductoService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest r) { return service.crear(r); }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) { return service.obtener(id); }

    @GetMapping
    public List<ProductoResponse> listar() { return service.listar(); }
}

