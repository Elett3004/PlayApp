package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.dto.BusquedaDTO;
import com.proyecto.PlayApp.dto.PedidoDetalleDTO;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.entity.Producto;
import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.repository.ProductoRepository;
import com.proyecto.PlayApp.repository.ResenaRepository;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.*;
import lombok.RequiredArgsConstructor;
import org.bson.BsonBinarySubType;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiAdminController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final ProductoService productoService;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReporteService reporteService;
    private final CurrentWeatherService currentWeatherService;
    private final WekaPredictionService wekaPredictionService;
    private final ResenaRepository resenaRepository;
    private final UsuarioService usuarioService;

    @Value("${playapp.admin.email:adminplayapp01@gmail.com}")
    private String adminGlobalCorreo;

    private boolean validarAdmin(String correo) {
        if (correo == null || correo.isBlank()) return false;
        Usuario u = usuarioRepository.findUsuarioByCorreo(correo.trim());
        return u != null && ("ROLE_ADMIN".equalsIgnoreCase(u.getRol()) || adminGlobalCorreo.equalsIgnoreCase(u.getCorreo()));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listarPedidosAdmin(
            @RequestParam(name = "correo") String correo,
            @RequestParam(name = "estado", required = false) Integer estado,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "20") Integer size
    ) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        Page<Pedido> pedidosPage = pedidoService.buscarPedidoPorRestaurate(
                BusquedaDTO.builder()
                        .id(correo.trim())
                        .categoria(estado)
                        .page(page)
                        .size(size)
                        .sort("[{\"campo\":\"timestamp\",\"direccion\":\"desc\"}]")
                        .build()
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (Pedido p : pedidosPage.getContent()) {
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
                map.put("metodoPago", "No registrado");
                map.put("estadoPago", "Sin estado");
                map.put("estadoPagoCss", "text-bg-secondary");
            }
            map.put("clienteNombre", p.getCliente() != null ? p.getCliente().getNombreCompleto() : "Cliente");
            map.put("clienteCorreo", p.getCliente() != null ? p.getCliente().getCorreo() : "");
            map.put("direccion", p.getEnvio() != null ? p.getEnvio().getDirreccion() : "");
            map.put("itemsCount", p.getCarrito() != null ? p.getCarrito().size() : 0);
            list.add(map);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("pedidos", list);
        res.put("totalElements", pedidosPage.getTotalElements());
        res.put("totalPages", pedidosPage.getTotalPages());
        res.put("currentPage", pedidosPage.getNumber());

        return ResponseEntity.ok(res);
    }

    @PostMapping("/orders/{id}/status")
    public ResponseEntity<?> cambiarEstadoPedido(
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        String correo = (String) body.get("correo");
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        Integer estado = ((Number) body.get("estado")).intValue();
        Pedido actualizado = pedidoService.actualizarEstadoPedido(estado, id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", actualizado.getId(),
                "estado", actualizado.getEstado(),
                "estadoNombre", actualizado.obtenerNombreEstado()
        ));
    }

    @PostMapping("/orders/{id}/mark-paid")
    public ResponseEntity<?> marcarPedidoPagado(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String correo = body.get("correo");
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        Pedido actualizado = pedidoService.marcarPedidoComoPagado(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", actualizado.getId(),
                "estadoPago", actualizado.getPago().getEstadoPago(),
                "mensaje", "Pedido marcado como pagado exitosamente"
        ));
    }

    @PostMapping("/products")
    public ResponseEntity<?> crearProducto(
            @RequestBody Map<String, Object> body
    ) {
        String correo = (String) body.get("correo");
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        String nombre = (String) body.get("nombre");
        String descripcion = (String) body.get("descripcion");
        Double precio = ((Number) body.get("precio")).doubleValue();
        Double stock = body.get("stock") != null ? ((Number) body.get("stock")).doubleValue() : 10.0;
        Integer categoria = body.get("categoria") != null ? ((Number) body.get("categoria")).intValue() : 1;
        Integer tipo = body.get("tipo") != null ? ((Number) body.get("tipo")).intValue() : 1;
        String base64Image = (String) body.get("base64Image");

        Producto nuevo = new Producto();
        nuevo.setNombre(nombre);
        nuevo.setDescripcion(descripcion);
        nuevo.setPrecio(precio);
        nuevo.setStock(stock);
        nuevo.setCategoria(categoria);
        nuevo.setTipo(tipo);

        if (base64Image != null && !base64Image.isBlank()) {
            try {
                String cleanBase64 = base64Image;
                String contentType = "image/jpeg";
                if (cleanBase64.contains(",")) {
                    String[] parts = cleanBase64.split(",");
                    if (parts[0].contains("image/png")) contentType = "image/png";
                    cleanBase64 = parts[1];
                }
                byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                nuevo.setImagen(new Binary(BsonBinarySubType.BINARY, decoded));
                nuevo.setImagenContentType(contentType);
            } catch (Exception ignored) {}
        }

        Producto guardado = productoService.crearProducto(nuevo, correo.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "id", guardado.getId(),
                "nombre", guardado.getNombre(),
                "mensaje", "Producto creado exitosamente"
        ));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> actualizarProducto(
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        String correo = (String) body.get("correo");
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        Optional<Producto> opt = productoService.buscarProductoPorId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }

        Producto existente = opt.get();
        if (body.containsKey("nombre")) existente.setNombre((String) body.get("nombre"));
        if (body.containsKey("descripcion")) existente.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("precio")) existente.setPrecio(((Number) body.get("precio")).doubleValue());
        if (body.containsKey("stock")) existente.setStock(((Number) body.get("stock")).doubleValue());
        if (body.containsKey("categoria")) existente.setCategoria(((Number) body.get("categoria")).intValue());

        if (body.containsKey("base64Image") && body.get("base64Image") != null) {
            try {
                String cleanBase64 = (String) body.get("base64Image");
                String contentType = "image/jpeg";
                if (cleanBase64.contains(",")) {
                    String[] parts = cleanBase64.split(",");
                    if (parts[0].contains("image/png")) contentType = "image/png";
                    cleanBase64 = parts[1];
                }
                byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                existente.setImagen(new Binary(BsonBinarySubType.BINARY, decoded));
                existente.setImagenContentType(contentType);
            } catch (Exception ignored) {}
        }

        Producto guardado = productoRepository.save(existente);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", guardado.getId(),
                "nombre", guardado.getNombre(),
                "mensaje", "Producto actualizado exitosamente"
        ));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> eliminarProducto(
            @PathVariable String id,
            @RequestParam(name = "correo") String correo
    ) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        productoService.deleteProducto(id);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Producto eliminado exitosamente"));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> obtenerMetricas(@RequestParam(name = "correo") String correo) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        long totalPedidos = pedidoRepository.count();
        long totalProductos = productoRepository.count();
        long totalUsuarios = usuarioRepository.count();

        Map<String, Object> metricas = new HashMap<>();
        metricas.put("totalPedidos", totalPedidos);
        metricas.put("totalProductos", totalProductos);
        metricas.put("totalUsuarios", totalUsuarios);
        metricas.put("ubicacion", currentWeatherService.location());
        try {
            metricas.put("clima", currentWeatherService.current());
        } catch (Exception e) {
            metricas.put("clima", Map.of("temperatura", "31°C", "condicion", "Soleado en Bocagrande"));
        }

        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/reports")
    public ResponseEntity<?> obtenerReportesAdmin(@RequestParam(name = "correo") String correo) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        Map<String, Object> reports = new HashMap<>();
        try {
            reports.put("pedidos", reporteService.reportePedidos(correo.trim()));
            reports.put("productosTop", reporteService.productosTop(10, correo.trim()));
            reports.put("ingresosMensuales", reporteService.ingresosMensuales(correo.trim()));
        } catch (Exception e) {
            reports.put("pedidos", Map.of("totalPedidos", 0, "totalIngresos", 0.0));
            reports.put("productosTop", List.of());
            reports.put("ingresosMensuales", List.of());
        }
        return ResponseEntity.ok(reports);
    }

    @PostMapping("/prediction/sales")
    public ResponseEntity<?> predecirVenta(@RequestBody Map<String, Object> body) {
        String correo = (String) body.get("correo");
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        String diaSemana = (String) body.getOrDefault("diaSemana", "Viernes");
        if (diaSemana != null) {
            diaSemana = diaSemana.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
            diaSemana = diaSemana.replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U");
        }
        String producto = (String) body.getOrDefault("producto", "Cerveza Club Colombia");
        String categoria = (String) body.getOrDefault("categoria", "Bebida");
        int precio = body.get("precio") != null ? ((Number) body.get("precio")).intValue() : 7000;
        String clima = (String) body.getOrDefault("clima", "Soleado");
        String temporada = (String) body.getOrDefault("temporada", "Alta");

        try {
            double resultado = wekaPredictionService.predecirVenta(
                    diaSemana, producto, categoria, precio, clima, temporada
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "prediccion", resultado,
                    "unidades", Math.round(resultado * 100.0) / 100.0,
                    "producto", producto,
                    "diaSemana", diaSemana
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error calculando la predicción: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listarUsuariosAdmin(@RequestParam(name = "correo") String correo) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        List<Map<String, Object>> res = new ArrayList<>();
        for (Usuario u : usuarios) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("nombre", u.getNombreCompleto() != null ? u.getNombreCompleto() : "");
            map.put("correo", u.getCorreo() != null ? u.getCorreo() : "");
            map.put("rol", u.getRol() != null ? u.getRol() : "ROLE_USER");
            map.put("esAdmin", "ROLE_ADMIN".equalsIgnoreCase(u.getRol()) || adminGlobalCorreo.equalsIgnoreCase(u.getCorreo()));
            res.add(map);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/users/create")
    public ResponseEntity<?> crearAdmin(@RequestBody Map<String, Object> body) {
        String adminCorreo = (String) body.get("adminCorreo");
        if (!validarAdmin(adminCorreo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador global"));
        }

        String nombreCompleto = (String) body.get("nombreCompleto");
        String correo = (String) body.get("correo");
        String password = (String) body.get("password");

        if (nombreCompleto == null || correo == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Todos los campos son obligatorios"));
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombreCompleto(nombreCompleto.trim());
        nuevo.setCorreo(correo.trim());
        nuevo.setPassword(password);

        try {
            usuarioService.createRestaurant(nuevo);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "mensaje", "Administrador registrado correctamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Error al registrar el administrador"
            ));
        }
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> listarResenasAdmin(@RequestParam(name = "correo") String correo) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        return ResponseEntity.ok(resenaRepository.findAllByOrderByFechaDesc());
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<?> eliminarResena(
            @PathVariable String id,
            @RequestParam(name = "correo") String correo
    ) {
        if (!validarAdmin(correo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos de administrador"));
        }

        resenaRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Reseña eliminada correctamente"));
    }
}
