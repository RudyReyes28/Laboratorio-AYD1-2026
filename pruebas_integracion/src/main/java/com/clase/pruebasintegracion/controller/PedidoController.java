package com.clase.pruebasintegracion.controller;
import com.clase.pruebasintegracion.dto.PedidoRequest;
import com.clase.pruebasintegracion.dto.PedidoResponse;
import com.clase.pruebasintegracion.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {
    private final PedidoService service;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody PedidoRequest r) { return service.crear(r); }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) { return service.obtener(id); }
}