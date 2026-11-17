package com.proyecto.PlayApp.service.payment;

import org.springframework.stereotype.Service;

import com.proyecto.PlayApp.entity.Pedido;

@Service
public class DemoPaymentService implements PaymentGatewayService {

    @Override
    public String getProveedorNombre() {
        return "DEMO";
    }

    @Override
    public boolean estaDisponible() {
        return true;
    }

    @Override
    public PaymentInitResponse iniciarPago(Pedido pedido, double valor) {
        String ordenId = (pedido != null) ? pedido.getId() : "orden_demo";
        String referencia = "demo_" + ordenId + "_" + System.currentTimeMillis();
        long montoEnCentavos = Math.round(valor * 100);

        return PaymentInitResponse.builder()
                .proveedor("DEMO")
                .ordenId(ordenId)
                .referencia(referencia)
                .montoEnCentavos(montoEnCentavos)
                .valor(valor)
                .moneda("COP")
                .widgetPublicKey("pub_demo_local")
                .signatureIntegrity("")
                .redirectUrl("/payment/proceed")
                .configurado(true)
                .mensaje("Modo Demo Local activo. Selecciona el resultado de prueba para tu orden.")
                .build();
    }

    @Override
    public PaymentVerificationResult verificarTransaccion(String transaccionId, String referencia) {
        PaymentStatus status = PaymentStatus.APROBADO;
        if (transaccionId != null) {
            String lower = transaccionId.toLowerCase();
            if (lower.contains("declined") || lower.contains("rechazado")) {
                status = PaymentStatus.RECHAZADO;
            } else if (lower.contains("canceled") || lower.contains("cancelado") || lower.contains("voided")) {
                status = PaymentStatus.CANCELADO;
            }
        }

        return PaymentVerificationResult.builder()
                .transaccionId(transaccionId != null ? transaccionId : "demo_trx_123")
                .referencia(referencia != null ? referencia : "demo_ref_123")
                .status(status)
                .metodoPago("Tarjeta Demo Local")
                .valor(0)
                .moneda("COP")
                .mensaje("Pago simulado exitosamente en Modo Demo (" + status.getDescripcion() + ")")
                .verificado(true)
                .build();
    }
}
