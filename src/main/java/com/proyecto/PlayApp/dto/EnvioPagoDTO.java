package com.proyecto.PlayApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvioPagoDTO {
    private Double latitud;
    private Double longitud;
    private String direccion;
    private String zona;
    private Integer mesa;
    private String descripcion;
    @Builder.Default
    private String metodoPago = "WOMPI";
    private Double valor;
}
