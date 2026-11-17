package com.proyecto.PlayApp.Controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.payment.PaymentGatewayManager;
import com.proyecto.PlayApp.service.payment.PaymentInitResponse;
import com.proyecto.PlayApp.service.payment.PaymentStatus;
import com.proyecto.PlayApp.service.payment.PaymentVerificationResult;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PagoController {

    private static final Logger logger = LoggerFactory.getLogger(PagoController.class);

    private final CarritoService carritos;
    private final PedidoService pedidos;
    private final PedidoRepository pedidoRepository;
    private final PaymentGatewayManager paymentManager;

    @GetMapping("/paymentgateway")
    public String mostarPasarelaDePagos(
            @RequestParam(name = "valor", required = false) Double valor,
            @RequestParam(name = "metodo", required = false) String metodo,
            @RequestParam(name = "orden", required = false) String orden,
            Model model
    ) {
        String metodoFinal = (metodo != null && !metodo.isBlank() && !"null".equalsIgnoreCase(metodo)) ? metodo : "WOMPI";
        Pedido pedido = (orden != null) ? pedidoRepository.findById(orden).orElse(null) : null;
        double valorFinal = (valor != null && valor > 0) ? valor : (pedido != null ? pedido.getTotal() : 0.0);

        double subtotal = 0;
        double costoEnvio = 5000.0;
        if (pedido != null && pedido.getCarrito() != null) {
            subtotal = pedido.getCarrito().stream()
                    .mapToDouble(item -> item.getSubtotal() != null ? item.getSubtotal() : 0.0)
                    .sum();
        }

        PaymentInitResponse paymentInit = paymentManager.prepararPago(pedido, valorFinal, metodoFinal);

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("costoEnvio", costoEnvio);
        model.addAttribute("valor", valorFinal);
        model.addAttribute("metodo", metodoFinal);
        model.addAttribute("orden", orden);
        model.addAttribute("pedido", pedido);
        model.addAttribute("paymentInit", paymentInit);
        return "fake-payment-gateway";
    }

    @GetMapping("/wompi/redirect")
    public String retornoWompi(
            Principal userInSession,
            @RequestParam(name = "id", required = false) String transaccionId,
            @RequestParam(name = "orden", required = false) String ordenId,
            Model model
    ) {
        logger.info("Recibida redirección de Wompi con transaccionId={} para orden={}", transaccionId, ordenId);
        PaymentVerificationResult resultado = paymentManager.verificar(transaccionId, null, "WOMPI");

        String ordenFinal = (ordenId != null && !ordenId.isBlank() && !"null".equalsIgnoreCase(ordenId))
                ? ordenId
                : resultado.getOrdenId();

        Pedido pedido = (ordenFinal != null) ? pedidoRepository.findById(ordenFinal).orElse(null) : null;
        model.addAttribute("puedeReintentar", false);
        model.addAttribute("ordenReintentar", null);
        model.addAttribute("success", null);
        model.addAttribute("error", null);
        model.addAttribute("info", null);
        model.addAttribute("contraentrega", false);
        model.addAttribute("mensajeContraentrega", null);

        String correo = (userInSession != null) ? userInSession.getName() : (pedido != null && pedido.getCliente() != null ? pedido.getCliente().getCorreo() : null);

        if (resultado.getStatus() == PaymentStatus.APROBADO) {
            if (ordenFinal != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(1, resultado.getMetodoPago(), ordenFinal);
                if (actualizado != null) pedido = actualizado;
            }
            if (correo != null) {
                carritos.limpiar(correo);
            }
            model.addAttribute("success", "¡Pago aprobado! Tu pedido está siendo preparado.");
        } else if (resultado.getStatus() == PaymentStatus.RECHAZADO) {
            if (ordenFinal != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(2, resultado.getMetodoPago(), ordenFinal);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("error", "Tu pago fue rechazado por la pasarela o la entidad bancaria. Por favor intenta con otro medio de pago o revisa los datos de tu tarjeta.");
            model.addAttribute("puedeReintentar", true);
            model.addAttribute("ordenReintentar", ordenFinal);
        } else if (resultado.getStatus() == PaymentStatus.PENDIENTE) {
            if (ordenFinal != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(3, resultado.getMetodoPago(), ordenFinal);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("info", "Tu pago se encuentra pendiente de confirmación en Wompi. Te avisaremos cuando el banco procese la transacción.");
        } else if (resultado.getStatus() == PaymentStatus.CANCELADO) {
            if (ordenFinal != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(4, resultado.getMetodoPago(), ordenFinal);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("error", "El proceso de pago fue cancelado.");
            model.addAttribute("puedeReintentar", true);
            model.addAttribute("ordenReintentar", ordenFinal);
        } else {
            if (ordenFinal != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(2, resultado.getMetodoPago(), ordenFinal);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("error", "El pago fue rechazado por la pasarela o la entidad bancaria. Por favor intenta con otro medio de pago.");
            model.addAttribute("puedeReintentar", true);
            model.addAttribute("ordenReintentar", ordenFinal);
        }

        model.addAttribute("pedido", pedido);
        return "pedido-confirmacion";
    }

    @GetMapping("/proceed")
    public String terminarPago(
            Principal userInSession,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "orden", required = false) String orden,
            @RequestParam(name = "metodo", required = false) String metodo,
            Model model
    ){
        Pedido pedido = (orden != null) ? pedidoRepository.findById(orden).orElse(null) : null;
        model.addAttribute("puedeReintentar", false);
        model.addAttribute("ordenReintentar", null);
        model.addAttribute("success", null);
        model.addAttribute("error", null);
        model.addAttribute("info", null);
        model.addAttribute("contraentrega", false);
        model.addAttribute("mensajeContraentrega", null);

        String correo = (userInSession != null) ? userInSession.getName() : (pedido != null && pedido.getCliente() != null ? pedido.getCliente().getCorreo() : null);

        String metodoFinal = "Tarjeta (Wompi)";
        if ("EFECTIVO_CONTRAENTREGA".equalsIgnoreCase(metodo)) {
            metodoFinal = "EFECTIVO_CONTRAENTREGA";
        } else if ("TRANSFERENCIA_CONTRAENTREGA".equalsIgnoreCase(metodo)) {
            metodoFinal = "TRANSFERENCIA_CONTRAENTREGA";
        } else if (metodo != null && !metodo.isBlank() && !"null".equalsIgnoreCase(metodo) && !"WOMPI_SANDBOX".equalsIgnoreCase(metodo)) {
            metodoFinal = metodo;
        }

        if ("CONTRAENTREGA".equalsIgnoreCase(estado) || "PENDIENTE_CONTRAENTREGA".equalsIgnoreCase(estado)
                || "EFECTIVO_CONTRAENTREGA".equalsIgnoreCase(metodo) || "TRANSFERENCIA_CONTRAENTREGA".equalsIgnoreCase(metodo)) {
            if (orden != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(6, metodoFinal, orden);
                if (actualizado != null) pedido = actualizado;
            }
            if (correo != null) {
                carritos.limpiar(correo);
            }
            model.addAttribute("contraentrega", true);
            model.addAttribute("mensajeContraentrega", "Pago pendiente contraentrega: paga al recibir tu pedido.");
        } else if ("SUCCESS".equalsIgnoreCase(estado) || "APROBADO".equalsIgnoreCase(estado)) {
            if (orden != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(1, metodoFinal, orden);
                if (actualizado != null) pedido = actualizado;
            }
            if (correo != null) {
                carritos.limpiar(correo);
            }
            model.addAttribute("success", "¡Pago aprobado! Tu pedido está siendo preparado.");
        } else if ("PENDIENTE".equalsIgnoreCase(estado)) {
            if (orden != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(3, metodoFinal, orden);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("info", "El pago se encuentra pendiente de acreditación en Wompi.");
        } else if ("CANCELED".equalsIgnoreCase(estado) || "CANCELADO".equalsIgnoreCase(estado)) {
            if (orden != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(4, metodoFinal, orden);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("error", "El proceso de pago fue cancelado.");
            model.addAttribute("puedeReintentar", true);
            model.addAttribute("ordenReintentar", orden);
        } else {
            if (orden != null) {
                Pedido actualizado = pedidos.actualizarEstadoYMetodoPagoPedido(2, metodoFinal, orden);
                if (actualizado != null) pedido = actualizado;
            }
            model.addAttribute("error", "Tu pago fue rechazado por la pasarela o la entidad bancaria. Por favor intenta con otro medio de pago o revisa los datos de tu tarjeta.");
            model.addAttribute("puedeReintentar", true);
            model.addAttribute("ordenReintentar", orden);
        }

        model.addAttribute("pedido", pedido);

        return "pedido-confirmacion";
    }

    @PostMapping("/wompi/webhook")
    @ResponseBody
    public ResponseEntity<String> wompiWebhook(@RequestBody(required = false) String payload) {
        logger.info("Evento webhook recibido de Wompi: {}", payload);
        return ResponseEntity.ok("OK");
    }
}
