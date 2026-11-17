package com.proyecto.PlayApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDetalleDTO {
    private String id;
    private String clienteNombre;
    private String clienteCorreo;
    private String fecha;
    private Double total;
    private String totalFormateado;
    private Double subtotal;
    private String subtotalFormateado;
    private Double costoEnvio;
    private String costoEnvioFormateado;
    private Integer estadoPedido;
    private String estadoPedidoNombre;
    private String estadoPedidoCss;
    private Integer estadoPago;
    private String estadoPagoNombre;
    private String estadoPagoCss;
    private String metodoPago;
    private String metodoPagoNombre;
    private String direccionEntrega;
    private String referenciaEntrega;
    private Integer mesa;
    private Integer carpa;
    private List<PedidoItemDetalleDTO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PedidoItemDetalleDTO {
        private String productoId;
        private String nombre;
        private String vendedor;
        private Integer cantidad;
        private Double precioUnitario;
        private String precioUnitarioFormateado;
        private Double subtotal;
        private String subtotalFormateado;
        private String imagenUrl;
    }
}
