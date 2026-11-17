package com.proyecto.PlayApp.service.payment;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitResponse {
    private String proveedor; // "WOMPI"
    private String ordenId;
    private String referencia;
    private long montoEnCentavos;
    private double valor;
    private String moneda; // "COP"
    private String widgetPublicKey;
    private String signatureIntegrity;
    private String redirectUrl;
    private boolean configurado;
    private List<String> variablesFaltantes;
    private String mensaje;
}
