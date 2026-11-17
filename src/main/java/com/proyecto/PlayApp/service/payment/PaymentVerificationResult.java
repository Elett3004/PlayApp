package com.proyecto.PlayApp.service.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationResult {
    private String ordenId;
    private String transaccionId;
    private String referencia;
    private PaymentStatus status;
    private String metodoPago;
    private double valor;
    private String moneda;
    private String mensaje;
    private boolean verificado;
}
