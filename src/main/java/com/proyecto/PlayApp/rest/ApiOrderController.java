package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.dto.CompraDTO;
import com.proyecto.PlayApp.dto.EnvioPagoDTO;
import com.proyecto.PlayApp.dto.PedidoDetalleDTO;
import com.proyecto.PlayApp.entity.CarritoItem;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiOrderController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final CarritoService carritoService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/create")
    public ResponseEntity<?> crearPedido(@RequestBody Map<String, Object> body) {
        String correo = (String) body.get("correo");
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Correo del usuario requerido"));
        }

        Usuario usuario = usuarioRepository.findUsuarioByCorreo(correo.trim());
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }

        String direccion = (String) body.getOrDefault("direccion", "Playa de Bocagrande, Cartagena");
        String zona = (String) body.getOrDefault("zona", "Bocagrande");
        String referencia = (String) body.getOrDefault("referencia", "");
        Integer carpa = body.get("carpa") != null ? ((Number) body.get("carpa")).intValue() : null;
        Integer mesa = body.get("mesa") != null ? ((Number) body.get("mesa")).intValue() : null;
        Double latitud = body.get("latitud") != null ? ((Number) body.get("latitud")).doubleValue() : 10.394598;
        Double longitud = body.get("longitud") != null ? ((Number) body.get("longitud")).doubleValue() : -75.557119;
        String metodoPago = (String) body.getOrDefault("metodoPago", "WOMPI");

        List<CarritoItem> items;
        if (body.containsKey("items") && body.get("items") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
            items = new ArrayList<>();
            for (Map<String, Object> ri : rawItems) {
                String pId = (String) ri.get("productoId");
                String nom = (String) ri.getOrDefault("nombre", "Producto");
                int cant = ((Number) ri.get("cantidad")).intValue();
                double prec = ((Number) ri.get("precio")).doubleValue();
                items.add(CarritoItem.builder()
                        .productoId(pId)
                        .nombre(nom)
                        .cantidad(cant)
                        .precio(prec)
                        .build());
            }
        } else {
            items = carritoService.listarCarrito(correo.trim());
        }

        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El carrito de compras está vacío"));
        }

        try {
            double subtotal = items.stream().mapToDouble(i -> i.getPrecio() * i.getCantidad()).sum();
            double costoEnvio = 5000.0;
            double total = subtotal + costoEnvio;

            Integer mesaFinal = (mesa != null) ? mesa : carpa;
            EnvioPagoDTO envioPagoDTO = EnvioPagoDTO.builder()
                    .direccion(direccion)
                    .zona(zona)
                    .descripcion(referencia)
                    .mesa(mesaFinal)
                    .latitud(latitud)
                    .longitud(longitud)
                    .metodoPago(metodoPago)
                    .valor(total)
                    .build();

            CompraDTO compraDTO = CompraDTO.builder()
                    .correoUsuario(correo.trim())
                    .carrito(items)
                    .envioPago(envioPagoDTO)
                    .build();

            Pedido pedido = pedidoService.RealizarPedido(compraDTO);

            // Limpiar carrito si el pedido se creó con éxito
            carritoService.limpiar(correo.trim());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "pedidoId", pedido.getId(),
                    "total", pedido.getTotal(),
                    "estado", pedido.getEstado(),
                    "estadoNombre", pedido.obtenerNombreEstado(),
                    "metodoPago", metodoPago,
                    "estadoPago", pedido.getPago() != null ? pedido.getPago().obtenerNombreEstado() : "Pendiente",
                    "mensaje", "Pedido creado exitosamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> listarPedidosUsuario(@RequestParam(name = "correo") String correo) {
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Correo no proporcionado"));
        }
        Usuario usuario = usuarioRepository.findUsuarioByCorreo(correo.trim());
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }

        List<Pedido> pedidos = pedidoRepository.findAllByClienteOrderByTimestampDesc(usuario);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Pedido p : pedidos) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("total", p.getTotal());
            map.put("fecha", p.getTimestamp() != null ? p.getTimestamp().toString() : "");
            map.put("estado", p.getEstado());
            map.put("estadoNombre", p.obtenerNombreEstado());
            map.put("estadoCss", p.obtenerClaseCSSEstado());
            if (p.getPago() != null) {
                map.put("metodoPago", p.getPago().obtenerNombreMetodo());
                map.put("estadoPago", p.getPago().obtenerNombreEstado());
                map.put("estadoPagoCss", p.getPago().obtenerClaseCSSEstado());
            } else {
                map.put("metodoPago", "Tarjeta (Wompi)");
                map.put("estadoPago", "Iniciado");
                map.put("estadoPagoCss", "text-bg-secondary");
            }
            map.put("totalProductos", p.getCarrito() != null ? p.getCarrito().size() : 0);
            response.add(map);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detallePedido(@PathVariable String id) {
        Optional<Pedido> opt = pedidoService.buscarPorId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        PedidoDetalleDTO dto = pedidoService.construirDetalleDTO(opt.get());
        return ResponseEntity.ok(dto);
    }
}
