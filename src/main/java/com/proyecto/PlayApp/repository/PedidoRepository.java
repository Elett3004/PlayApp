package com.proyecto.PlayApp.repository;

import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findAllByClienteOrderByTimestampDesc(Usuario cliente);
}
