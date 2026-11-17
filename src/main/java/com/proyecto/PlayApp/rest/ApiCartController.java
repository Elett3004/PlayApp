package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.dto.ItemDTO;
import com.proyecto.PlayApp.entity.CarritoItem;
import com.proyecto.PlayApp.entity.Producto;
import com.proyecto.PlayApp.repository.ProductoRepository;
import com.proyecto.PlayApp.service.CarritoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiCartController {

    private final CarritoService carritoService;
    private final ProductoRepository productoRepository;

    @GetMapping
    public ResponseEntity<?> obtenerCarrito(@RequestParam(name = "correo") String correo) {
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Correo no proporcionado"));
        }
        List<CarritoItem> items;
        try {
            items = carritoService.listarCarrito(correo.trim());
        } catch (Exception e) {
            items = new ArrayList<>();
        }

        double subtotal = 0.0;
        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (CarritoItem item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("productoId", item.getProductoId());
            map.put("nombre", item.getNombre());
            map.put("cantidad", item.getCantidad());
            map.put("precio", item.getPrecio());
            double itemSubtotal = item.getPrecio() * item.getCantidad();
            map.put("subtotal", itemSubtotal);
            subtotal += itemSubtotal;

            Optional<Producto> p = productoRepository.findById(item.getProductoId());
            if (p.isPresent()) {
                map.put("imageSRC", p.get().getImageSRC());
                map.put("stock", p.get().getStock());
                map.put("categoria", p.get().getCategoria());
            } else {
                map.put("imageSRC", "/img/blank-product.jpg");
                map.put("stock", 0.0);
            }
            itemsList.add(map);
        }

        double costoEnvio = itemsList.isEmpty() ? 0.0 : 5000.0;
        double total = subtotal + costoEnvio;

        Map<String, Object> response = new HashMap<>();
        response.put("items", itemsList);
        response.put("subtotal", subtotal);
        response.put("costoEnvio", costoEnvio);
        response.put("total", total);
        response.put("cantidadTotal", itemsList.stream().mapToInt(i -> (Integer) i.get("cantidad")).sum());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/add")
    public ResponseEntity<?> agregarItem(@RequestBody Map<String, Object> body) {
        String correo = (String) body.get("correo");
        String itemId = (String) body.get("itemId");
        int cantidad = body.get("cantidad") != null ? ((Number) body.get("cantidad")).intValue() : 1;

        if (correo == null || itemId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "correo e itemId requeridos"));
        }

        try {
            carritoService.agregar(ItemDTO.builder()
                    .correo(correo.trim())
                    .itemId(itemId.trim())
                    .cantidad(cantidad)
                    .build());
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Producto agregado al carrito"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> actualizarCantidad(@RequestBody Map<String, Object> body) {
        String correo = (String) body.get("correo");
        String itemId = (String) body.get("itemId");
        int cantidad = body.get("cantidad") != null ? ((Number) body.get("cantidad")).intValue() : 1;

        if (correo == null || itemId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "correo e itemId requeridos"));
        }

        try {
            if (cantidad <= 0) {
                carritoService.eliminar(correo.trim(), itemId.trim());
            } else {
                carritoService.agregar(ItemDTO.builder()
                        .correo(correo.trim())
                        .itemId(itemId.trim())
                        .cantidad(cantidad)
                        .build());
            }
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Cantidad actualizada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> eliminarItem(@RequestBody Map<String, String> body) {
        String correo = body.get("correo");
        String itemId = body.get("itemId");

        if (correo == null || itemId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "correo e itemId requeridos"));
        }

        carritoService.eliminar(correo.trim(), itemId.trim());
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Producto eliminado"));
    }

    @PostMapping("/clear")
    public ResponseEntity<?> limpiarCarrito(@RequestBody Map<String, String> body) {
        String correo = body.get("correo");
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "correo requerido"));
        }
        carritoService.limpiar(correo.trim());
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Carrito vaciado"));
    }
}
