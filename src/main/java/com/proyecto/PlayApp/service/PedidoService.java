package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.PlayApp.dto.*;
import com.proyecto.PlayApp.entity.*;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.repository.ProductoRepository;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PedidoService {
    private final PedidoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final MongoTemplate mongoTemplate;

    public Pedido RealizarPedido(CompraDTO compra){
        Usuario usuario = usuarios.findUsuarioByCorreo(compra.getCorreoUsuario());
        if (compra.getCarrito().isEmpty()) throw new IllegalStateException("Carrito vacio");

        double subtotal = compra.getCarrito().stream()
                .mapToDouble(item -> item.getPrecio() * item.getCantidad())
                .sum();
        double costoEnvio = 5000.0;
        double total = subtotal + costoEnvio;
        compra.getEnvioPago().setValor(total);
        Envio envio = crearEnvio(compra.getEnvioPago(), usuario);
        Pago pago = crearPago(compra.getEnvioPago(),usuario);


        Pedido order = Pedido.builder()
                .estado(0)
                .total(total)
                .cliente(usuario)
                .envio(envio)
                .pago(pago)
                .timestamp(LocalDateTime.now())
                .build();
        DetallesDTO detalles = extraerDetalles(compra.getCarrito());
        order.setCarrito(detalles.getCarrito());
        order.setRestaurantes(detalles.getRestaurantes());
        return pedidos.save(order);
    }

    private Envio crearEnvio(EnvioPagoDTO datos , Usuario usuario){
        String descCompleta = datos.getDescripcion();
        if (datos.getZona() != null && !datos.getZona().isBlank()) {
            descCompleta = "[" + datos.getZona() + "] " + (descCompleta != null ? descCompleta : "");
        }
        return Envio.builder()
                .latitud(datos.getLatitud())
                .longitud(datos.getLongitud())
                .dirreccion(datos.getDireccion())
                .descripcion(descCompleta)
                .carpa(datos.getMesa())
                .mesa(datos.getMesa())
                .fecha(LocalDateTime.now())
                .clienteId(usuario.getId())
                .build();
    }

    private Pago crearPago(EnvioPagoDTO datos , Usuario usuario){
        String metodo = (datos.getMetodoPago() != null && !datos.getMetodoPago().isBlank())
                ? datos.getMetodoPago() : "WOMPI";

        Integer estado = 0;
        String estadoPago = "INICIADO";
        if ("EFECTIVO_CONTRAENTREGA".equalsIgnoreCase(metodo) || "TRANSFERENCIA_CONTRAENTREGA".equalsIgnoreCase(metodo)) {
            estado = 6;
            estadoPago = "PENDIENTE_CONTRAENTREGA";
        }

        return Pago.builder()
                .valor(datos.getValor())
                .metodo(metodo)
                .estado(estado)
                .estadoPago(estadoPago)
                .clienteId(usuario.getId())
                .fecha(LocalDateTime.now())
                .build();
    }


    private DetallesDTO extraerDetalles(List<CarritoItem> items){
        List<PedidoItem> carrito = new ArrayList<>();
        Set<String> restaurantes = new HashSet<>();

        items.forEach(carritoItem -> {
            Producto producto =  productos.findById(carritoItem.getProductoId()).orElse(new Producto());

            if(producto.getStock()<1|| producto.getStock()< carritoItem.getCantidad()){
                throw new IllegalStateException("Producto sin stock");
            }

            PedidoItem item = PedidoItem.builder()
                    .producto(producto)
                    .subtotal(carritoItem.getPrecio() * carritoItem.getCantidad())
                    .cantidad(carritoItem.getCantidad())
                    .build();

            carrito.add(item);
            restaurantes.add(producto.getEntidad().getId());
            actualizarCatalogo(producto,carritoItem.getCantidad());
        });

        return DetallesDTO.builder()
                .carrito(carrito)
                .restaurantes(new ArrayList<>(restaurantes))
                .build();
    }

    private void actualizarCatalogo(Producto producto, Integer cantidad){
        double nuevoStock = (producto.getStock() - cantidad);
        producto.setStock(nuevoStock);
        productos.save(producto);
    }

    public Page<Pedido> buscarPedidoConPaginaOrdenFiltro(BusquedaDTO busqueda) {
        // Creación y conversion de orden de registros
        List<OrdenDTO> ordenes = jsonStringToOrdenDTO(busqueda.getSort());
        List<Sort.Order> ordenado = construirOrden(ordenes);


        // Creación de solicitud de orden
        PageRequest solicitudPagina = PageRequest.of(
                busqueda.getPage(),
                busqueda.getSize(),
                Sort.by(ordenado)
        );

        Query query = new Query();
        Usuario usuario = usuarios.findById(busqueda.getId()).orElse(new Usuario());
        query.addCriteria(Criteria.where("cliente").is(usuario));
        if(busqueda.getCategoria() != null) {
            query.addCriteria(Criteria.where("estado").is(busqueda.getCategoria()));
        }
        query.with(solicitudPagina);

        // Retornar registros de acuerdo especificación y paginación
        return PageableExecutionUtils.getPage(
                mongoTemplate.find(query, Pedido.class),
                solicitudPagina,
                () -> mongoTemplate.count(query.skip(0).limit(0), Pedido.class)
        );
    }

    private List<OrdenDTO> jsonStringToOrdenDTO(String jsonString) {
        try {
            ObjectMapper obj = new ObjectMapper();
            return obj.readValue(jsonString, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private List<Sort.Order> construirOrden(List<OrdenDTO> ordenes){
        List<Sort.Order> ordenado = new ArrayList<>();
        if (ordenes != null) {
            for(OrdenDTO sort: ordenes) {
                Sort.Direction direction = Objects.equals(sort.getDireccion(), "desc")
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                ordenado.add(new Sort.Order(direction,sort.getCampo()));
            }
        }
        return ordenado;
    }

    public Pedido actualizarEstadoPagoPedido(Integer estado, String id){
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new IllegalStateException("Pedido no encontrado"));
        if (pedido.getPago() == null) {
            pedido.setPago(Pago.builder()
                    .metodo("WOMPI")
                    .estado(estado)
                    .valor(pedido.getTotal())
                    .fecha(LocalDateTime.now())
                    .build());
        } else {
            pedido.getPago().setEstado(estado);
        }
        return pedidos.save(pedido);
    }

    public Pedido actualizarEstadoYMetodoPagoPedido(Integer estado, String metodo, String id){
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new IllegalStateException("Pedido no encontrado"));
        String estadoPago = switch (estado) {
            case 1 -> "APROBADO";
            case 2 -> "RECHAZADO";
            case 3 -> "PENDIENTE";
            case 4 -> "CANCELADO";
            case 6 -> "PENDIENTE_CONTRAENTREGA";
            default -> "ESTADO_" + estado;
        };
        if (pedido.getPago() == null) {
            pedido.setPago(Pago.builder()
                    .metodo(metodo != null && !metodo.isBlank() ? metodo : "WOMPI")
                    .estado(estado)
                    .estadoPago(estadoPago)
                    .valor(pedido.getTotal())
                    .fecha(LocalDateTime.now())
                    .build());
        } else {
            pedido.getPago().setEstado(estado);
            pedido.getPago().setEstadoPago(estadoPago);
            if (metodo != null && !metodo.isBlank()) {
                pedido.getPago().setMetodo(metodo);
            }
        }
        return pedidos.save(pedido);
    }

    public Pedido marcarPedidoComoPagado(String pedidoId) {
        Pedido pedido = pedidos.findById(pedidoId)
                .orElseThrow(() -> new IllegalStateException("Pedido no encontrado: " + pedidoId));
        if (pedido.getPago() == null) {
            pedido.setPago(Pago.builder()
                    .metodo("EFECTIVO_CONTRAENTREGA")
                    .estado(1)
                    .estadoPago("PAGADO")
                    .valor(pedido.getTotal())
                    .fecha(LocalDateTime.now())
                    .build());
        } else {
            pedido.getPago().setEstado(1);
            pedido.getPago().setEstadoPago("PAGADO");
            pedido.getPago().setFecha(LocalDateTime.now());
        }
        return pedidos.save(pedido);
    }

    public Page<Pedido> buscarPedidoPorRestaurate(BusquedaDTO busqueda) {
        // Creación y conversion de orden de registros
        List<OrdenDTO> ordenes = jsonStringToOrdenDTO(busqueda.getSort());
        List<Sort.Order> ordenado = construirOrden(ordenes);


        // Creación de solicitud de orden
        PageRequest solicitudPagina = PageRequest.of(
                busqueda.getPage(),
                busqueda.getSize(),
                Sort.by(ordenado)
        );

        Query query = new Query();
        Usuario usuario = usuarios.findUsuarioByCorreo(busqueda.getId());
        query.addCriteria(Criteria.where("restaurantes").is(usuario.getId()));

        if(busqueda.getCategoria() != null) {
            query.addCriteria(Criteria.where("estado").is(busqueda.getCategoria()));
        }
        query.with(solicitudPagina);

        // Retornar registros de acuerdo especificación y paginación
        return PageableExecutionUtils.getPage(
                mongoTemplate.find(query, Pedido.class),
                solicitudPagina,
                () -> mongoTemplate.count(query.skip(0).limit(0), Pedido.class)
        );
    }

    public Pedido actualizarEstadoPedido(Integer estado, String id){
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new IllegalStateException("Pedido no encontrado"));
        pedido.setEstado(estado);
        return pedidos.save(pedido);
    }




}
