package com.proyecto.PlayApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarritoDetalleItemDTO {
    private String productoId;
    private String nombre;
    private String vendedor;
    private String imagenSrc;
    private int cantidad;
    private double precio;
    private double stock;
    private double subtotal;
}
