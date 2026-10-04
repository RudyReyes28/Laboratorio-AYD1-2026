package com.clase.pruebasintegracion.repository;

import com.clase.pruebasintegracion.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {}