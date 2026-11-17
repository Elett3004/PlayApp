package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.dto.CompraDTO;
import com.proyecto.PlayApp.dto.EnvioPagoDTO;
import com.proyecto.PlayApp.entity.CarritoItem;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/shipment")
public class EnvioController {
    private final PedidoService pedidos;
    private final CarritoService carritos;

    @GetMapping("/place")
    public String mostrarEnvio(
            Principal userInSession,
            Model model
    ) {
        if (userInSession == null) {
            return "redirect:/login";
        }
        List<CarritoItem> checkout = carritos.listarCarrito(userInSession.getName());
        if (checkout.isEmpty()) {
            return "redirect:/cart/checkout";
        }

        double subtotal = calcularTotalCarrito(checkout);
        double costoEnvio = 5000.0;
        double total = subtotal + costoEnvio;

        EnvioPagoDTO dto = EnvioPagoDTO.builder()
                .latitud(10.394598592365298)
                .longitud(-75.5571199005398)
                .direccion("Playa de Bocagrande, Cartagena")
                .zona("Bocagrande")
                .metodoPago("WOMPI")
                .valor(total)
                .build();

        model.addAttribute("carrito", checkout.size());
        model.addAttribute("items", checkout);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("costoEnvio", costoEnvio);
        model.addAttribute("total", total);
        model.addAttribute("envioPago", dto);
        return "shipment-payment";
    }

    @PostMapping("/proceed")
    public String crearNuevoEnvio(
            Principal userInSession,
            @ModelAttribute("envioPago") EnvioPagoDTO envioPago,
            Model model
    ){
        try{
            if (userInSession == null) {
                return "redirect:/login";
            }
            List<CarritoItem> items = carritos.listarCarrito(userInSession.getName());
            if (items.isEmpty()) {
                throw new IllegalStateException("El carrito de compras está vacío.");
            }

            if (envioPago.getDireccion() == null || envioPago.getDireccion().trim().length() < 3) {
                throw new IllegalArgumentException("Por favor ingresa o selecciona en el mapa una dirección de entrega válida.");
            }

            if (envioPago.getMetodoPago() == null || envioPago.getMetodoPago().isBlank()) {
                envioPago.setMetodoPago("WOMPI");
            }

            double subtotal = calcularTotalCarrito(items);
            double costoEnvio = 5000.0;
            double total = subtotal + costoEnvio;
            envioPago.setValor(total);

            CompraDTO compra = CompraDTO.builder()
                    .correoUsuario(userInSession.getName())
                    .carrito(items)
                    .envioPago(envioPago)
                    .build();

            Pedido pedido = pedidos.RealizarPedido(compra);

            // Si se selecciona pago contraentrega (efectivo o transferencia), confirmar sin llamar pasarelas externas
            if ("EFECTIVO_CONTRAENTREGA".equalsIgnoreCase(envioPago.getMetodoPago())
                    || "TRANSFERENCIA_CONTRAENTREGA".equalsIgnoreCase(envioPago.getMetodoPago())) {
                carritos.limpiar(userInSession.getName());
                return "redirect:/payment/proceed?estado=CONTRAENTREGA&orden="
                        + pedido.getId()
                        + "&metodo=" + envioPago.getMetodoPago();
            }

            return "redirect:/payment/paymentgateway?valor="
                    + pedido.getTotal()
                    + "&metodo=" + envioPago.getMetodoPago()
                    + "&orden=" + pedido.getId();
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "pedido-confirmacion";
        }
    }

    private double calcularTotalCarrito(List<CarritoItem> compra) {
        return compra.stream()
                .mapToDouble(item -> item.getPrecio() * item.getCantidad())
                .sum();
    }
}
