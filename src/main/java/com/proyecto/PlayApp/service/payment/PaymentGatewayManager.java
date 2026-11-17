package com.proyecto.PlayApp.service.payment;

import org.springframework.stereotype.Service;

import com.proyecto.PlayApp.entity.Pedido;

@Service
public class PaymentGatewayManager {

    private final WompiPaymentService wompiService;
    private final DemoPaymentService demoService;

    public PaymentGatewayManager(WompiPaymentService wompiService, DemoPaymentService demoService) {
        this.wompiService = wompiService;
        this.demoService = demoService;
    }

    public PaymentGatewayService getService(String metodoSolicitado) {
        if ("DEMO".equalsIgnoreCase(metodoSolicitado)) {
            return demoService;
        }
        if (wompiService.estaDisponible()) {
            return wompiService;
        }
        return demoService;
    }

    public PaymentInitResponse prepararPago(Pedido pedido, double valor, String metodoSolicitado) {
        return wompiService.iniciarPago(pedido, valor);
    }

    public PaymentVerificationResult verificar(String transaccionId, String referencia, String proveedor) {
        if ("DEMO".equalsIgnoreCase(proveedor) || (transaccionId != null && transaccionId.startsWith("demo_"))) {
            return demoService.verificarTransaccion(transaccionId, referencia);
        }
        return wompiService.verificarTransaccion(transaccionId, referencia);
    }
}
