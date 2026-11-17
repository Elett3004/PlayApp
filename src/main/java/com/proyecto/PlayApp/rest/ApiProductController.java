package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.dto.BusquedaDTO;
import com.proyecto.PlayApp.entity.Producto;
import com.proyecto.PlayApp.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiProductController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<?> listarProductos(
            @RequestParam(name = "nombre", required = false) String nombre,
            @RequestParam(name = "categoria", required = false) Integer categoria,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "30") Integer size,
            @RequestParam(name = "sort", defaultValue = "[{\"campo\":\"nombre\",\"direccion\":\"asc\"}]") String sort
    ) {
        Page<Producto> pageResult = productoService.buscarProductoConPaginaOrdenFiltro(
                BusquedaDTO.builder()
                        .nombre(nombre)
                        .categoria(categoria)
                        .page(page)
                        .size(size)
                        .sort(sort)
                        .build()
        );

        List<Map<String, Object>> items = new ArrayList<>();
        for (Producto p : pageResult.getContent()) {
            items.add(toMap(p));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("productos", items);
        response.put("totalElements", pageResult.getTotalElements());
        response.put("totalPages", pageResult.getTotalPages());
        response.put("currentPage", pageResult.getNumber());
        response.put("hasNext", pageResult.hasNext());
        response.put("hasPrevious", pageResult.hasPrevious());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerProducto(@PathVariable String id) {
        Optional<Producto> opt = productoService.buscarProductoPorId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        return ResponseEntity.ok(toMap(opt.get()));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable String id) {
        Optional<Producto> opt = productoService.buscarProductoPorId(id);
        if (opt.isEmpty() || opt.get().getImagen() == null) {
            return ResponseEntity.notFound().build();
        }
        Producto p = opt.get();
        byte[] bytes = p.getImagen().getData();
        String contentType = p.getImagenContentType() != null ? p.getImagenContentType() : "image/jpeg";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private Map<String, Object> toMap(Producto p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("nombre", p.getNombre());
        map.put("descripcion", p.getDescripcion());
        map.put("precio", p.getPrecio() != null ? p.getPrecio() : 0.0);
        map.put("stock", p.getStock() != null ? p.getStock() : 0.0);
        map.put("categoria", p.getCategoria() != null ? p.getCategoria() : 1);
        map.put("categoriaNombre", p.obtenerNombreCategoria());
        map.put("tipo", p.getTipo());
        map.put("imageSRC", p.getImageSRC());
        map.put("hasCustomImage", p.getImagen() != null);
        if (p.getEntidad() != null) {
            map.put("vendedorId", p.getEntidad().getId());
            map.put("vendedorNombre", p.getEntidad().getNombreCompleto());
        } else {
            map.put("vendedorNombre", "PlayApp Bocagrande");
        }
        return map;
    }
}
