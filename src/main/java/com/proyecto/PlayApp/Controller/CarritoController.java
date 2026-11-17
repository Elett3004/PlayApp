package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.dto.CarritoDetalleItemDTO;
import com.proyecto.PlayApp.dto.ItemDTO;
import com.proyecto.PlayApp.entity.CarritoItem;
import com.proyecto.PlayApp.entity.Producto;
import com.proyecto.PlayApp.repository.ProductoRepository;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CarritoController {
    private final CarritoService servicio;
    private final UsuarioService usuarios;
    private final ProductoRepository productos;

    @GetMapping("/add/{item}")
    public String addItem(
            @PathVariable  String item,
            @RequestParam(name = "cantidad", defaultValue = "1") Integer cantidad,
            Principal usuario
    ) {
        try{
            servicio.agregar(
                    ItemDTO.builder()
                            .itemId(item)
                            .cantidad(cantidad)
                            .correo(usuario.getName())
                            .build()
            );
            return "redirect:/shop?status=true";

        } catch (Exception e) {
            return "redirect:/shop?error=true";
        }
    }

    @GetMapping("/update/{item}")
    public String updateItem(
            @PathVariable String item,
            @RequestParam(name = "cantidad") Integer cantidad,
            Principal usuario
    ) {
        try {
            if (cantidad == null || cantidad < 1) {
                servicio.eliminar(usuario.getName(), item);
            } else {
                Producto prod = productos.findById(item).orElse(null);
                int stockMax = (prod != null && prod.getStock() != null) ? prod.getStock().intValue() : 99;
                int cantidadFinal = Math.min(cantidad, Math.max(1, stockMax));
                servicio.agregar(
                        ItemDTO.builder()
                                .itemId(item)
                                .cantidad(cantidadFinal)
                                .correo(usuario.getName())
                                .build()
                );
            }
        } catch (Exception e) {
            // Error controlado
        }
        return "redirect:/cart/checkout";
    }

    @GetMapping("/rm/{item}")
    public String removeItem(
            @PathVariable  String item,
            Principal usuario
    ) {
        try{
            servicio.eliminar(
                    usuario.getName(),
                    item
            );
            return "redirect:/cart/checkout";

        } catch (Exception e) {
            return "redirect:/cart/checkout";
        }
    }

    @GetMapping("/checkout")
    public String visualizarCarritoDeCompra(Principal usuario, Model model) {
        List<CarritoItem> checkout = (usuario != null) ? servicio.listarCarrito(usuario.getName()) : new ArrayList<>();
        List<CarritoDetalleItemDTO> itemsDetallados = new ArrayList<>();
        double subtotal = 0;

        for (CarritoItem item : checkout) {
            Producto prod = productos.findById(item.getProductoId()).orElse(null);
            String vendedor = (prod != null && prod.getEntidad() != null) ? prod.getEntidad().getNombreCompleto() : "PlayApp Delivery";
            String imagenSrc = (prod != null) ? prod.getImageSRC() : "/img/blank-product.jpg";
            double stock = (prod != null && prod.getStock() != null) ? prod.getStock() : 99;
            double itemSubtotal = item.getPrecio() * item.getCantidad();
            subtotal += itemSubtotal;

            itemsDetallados.add(CarritoDetalleItemDTO.builder()
                    .productoId(item.getProductoId())
                    .nombre(item.getNombre())
                    .vendedor(vendedor)
                    .imagenSrc(imagenSrc)
                    .cantidad(item.getCantidad())
                    .precio(item.getPrecio())
                    .stock(stock)
                    .subtotal(itemSubtotal)
                    .build());
        }

        double costoEnvio = checkout.isEmpty() ? 0 : 5000.0;
        double total = subtotal + costoEnvio;

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("costoEnvio", costoEnvio);
        model.addAttribute("total", total);
        model.addAttribute("carrito", checkout.size());
        model.addAttribute("checkout", checkout);
        model.addAttribute("items", itemsDetallados);

        String nombreusuario = "";
        if (usuario != null) {
            nombreusuario = usuarios.buscarUsuario(usuario.getName()).getNombreCompleto();
        }

        model.addAttribute("nombreUsuario", nombreusuario);
        return "checkout";
    }

    @GetMapping("/checkout/proceed")
    public String procederCompra(Principal usuario, Model model) {
        try {
            List<CarritoItem> checkout = servicio.listarCarrito(usuario.getName());
            if(checkout.isEmpty()){
                throw new RuntimeException("El carrito esta vacio, dirijase a la tienda para llenarlo.");
            }
            return "redirect:/shipment/place";
        }catch (Exception e){
            model.addAttribute("error", e.getMessage());
            return "pedido-confirmacion";
        }
    }
}
