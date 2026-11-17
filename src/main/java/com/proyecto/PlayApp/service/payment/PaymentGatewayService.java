package com.proyecto.PlayApp.service.payment;

import com.proyecto.PlayApp.entity.Pedido;

public interface PaymentGatewayService {
    String getProveedorNombre();
    boolean estaDisponible();
    PaymentInitResponse iniciarPago(Pedido pedido, double valor);
    PaymentVerificationResult verificarTransaccion(String transaccionId, String referencia);
}
