package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.service.PaymentService;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/mercadopago")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/mercadopago")
    public String mercadopago(
            @RequestParam("valor") double valor,
            @RequestParam("orden") String orden
    ) {
        try {
            String url = paymentService.crearPreferencia(
                    "Pedido #" + orden,
                    new BigDecimal(valor),
                    1,
                    orden
            );

            return "redirect:" + url;

        } catch (Exception e) {
            logger.error("No fue posible crear la preferencia de Mercado Pago para la orden {}", orden, e);
            String redirectUrl = UriComponentsBuilder.fromPath("/payment/paymentgateway")
                    .queryParam("valor", valor)
                    .queryParam("orden", orden)
                    .queryParam("metodo", "Mercado Pago")
                    .queryParam("error", "No fue posible conectar con Mercado Pago. Revisa el token de acceso e intenta nuevamente.")
                    .build()
                    .encode()
                    .toUriString();

            return "redirect:" + redirectUrl;
        }
    }

    // @GetMapping("/proceed")
    // public String terminarPago(
    //         Principal userInSession,
    //         @RequestParam(name = "estado", required = false) String estado,
    //         @RequestParam(name = "orden", required = false) String orden,
    //         Model model
    // ){
    //     carritos.limpiar(userInSession.getName());
    //     model.addAttribute("pedidos", carritos.listarCarrito(userInSession.getName()));
    //     if(!estado.equalsIgnoreCase("SUCCESS")){
    //         model.addAttribute("error", "Ha ocurrido un error...");
    //         pedidos.actualizarEstadoPagoPedido(2, orden);
    //         return "pedido-confirmacion";
    //     }

    //     pedidos.actualizarEstadoPagoPedido(1, orden);
    //     model.addAttribute("success", "Compra realizada con exito!");

    //     return "pedido-confirmacion";
    // }
}
