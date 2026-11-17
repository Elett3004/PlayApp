package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.payment.PaymentGatewayManager;
import com.proyecto.PlayApp.service.payment.PaymentInitResponse;
import com.proyecto.PlayApp.service.payment.PaymentStatus;
import com.proyecto.PlayApp.service.payment.PaymentVerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiPaymentController {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final PaymentGatewayManager paymentGatewayManager;

    @GetMapping("/wompi/init")
    public ResponseEntity<?> prepararPagoWompi(
            @RequestParam(name = "ordenId") String ordenId,
            @RequestParam(name = "valor", required = false) Double valor
    ) {
        if (ordenId == null || ordenId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ordenId requerido"));
        }

        Optional<Pedido> opt = pedidoRepository.findById(ordenId.trim());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }

        Pedido pedido = opt.get();
        double valorFinal = (valor != null && valor > 0) ? valor : pedido.getTotal();
        PaymentInitResponse response = paymentGatewayManager.prepararPago(pedido, valorFinal, "WOMPI");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/wompi/verify")
    public ResponseEntity<?> verificarPagoWompi(@RequestBody Map<String, String> body) {
        String transaccionId = body.get("transaccionId");
        String ordenId = body.get("ordenId");

        if (transaccionId == null || transaccionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "transaccionId requerido"));
        }

        PaymentVerificationResult resultado = paymentGatewayManager.verificar(transaccionId.trim(), null, "WOMPI");

        String ordenFinal = (ordenId != null && !ordenId.isBlank()) ? ordenId : resultado.getOrdenId();
        if (ordenFinal != null) {
            int estadoNumero = (resultado.getStatus() == PaymentStatus.APROBADO) ? 1 : 2;
            pedidoService.actualizarEstadoYMetodoPagoPedido(estadoNumero, resultado.getMetodoPago(), ordenFinal);
        }

        return ResponseEntity.ok(Map.of(
                "status", resultado.getStatus().name(),
                "mensaje", resultado.getMensaje(),
                "transaccionId", resultado.getTransaccionId(),
                "referencia", resultado.getReferencia(),
                "metodoPago", resultado.getMetodoPago(),
                "ordenId", ordenFinal != null ? ordenFinal : ""
        ));
    }
}
