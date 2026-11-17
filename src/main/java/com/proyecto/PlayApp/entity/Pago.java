package com.proyecto.PlayApp.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "pagos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    private String id;
    private String metodo;
    private double valor;
    private Integer estado;
    private String estadoPago;
    private LocalDateTime fecha;
    private String clienteId;

    public String obtenerNombreEstado() {
        if ("PENDIENTE_CONTRAENTREGA".equalsIgnoreCase(estadoPago) || (estado != null && estado == 6)) {
            return "Pendiente contraentrega";
        }
        if ("PAGADO".equalsIgnoreCase(estadoPago) || (estado != null && estado == 1)) {
            return "Pagado";
        }
        if (estado == null) return "Sin Estado";
        return switch (estado) {
            case 0 -> "Iniciado";
            case 1 -> "Aprobado";
            case 2 -> "Rechazado";
            case 3 -> "Pendiente";
            case 4 -> "Cancelado";
            case 5 -> "Error";
            case 6 -> "Pendiente contraentrega";
            default -> (estadoPago != null && !estadoPago.isBlank()) ? estadoPago : "Estado #" + estado;
        };
    }

    public String obtenerClaseCSSEstado() {
        if ("PENDIENTE_CONTRAENTREGA".equalsIgnoreCase(estadoPago) || (estado != null && estado == 6)) {
            return "text-bg-warning";
        }
        if ("PAGADO".equalsIgnoreCase(estadoPago) || (estado != null && estado == 1)) {
            return "text-bg-success";
        }
        if (estado == null) return "text-bg-secondary";
        return switch (estado) {
            case 0 -> "text-bg-info";
            case 1 -> "text-bg-success";
            case 2 -> "text-bg-danger";
            case 3 -> "text-bg-warning";
            case 4 -> "text-bg-secondary";
            case 5 -> "text-bg-danger";
            case 6 -> "text-bg-warning";
            default -> "text-bg-secondary";
        };
    }

    public String obtenerNombreMetodo() {
        if (metodo == null || metodo.isBlank()) return "Tarjeta (Wompi)";
        return switch (metodo.toUpperCase()) {
            case "EFECTIVO_CONTRAENTREGA" -> "Efectivo contraentrega";
            case "TRANSFERENCIA_CONTRAENTREGA" -> "Transferencia contraentrega";
            case "WOMPI", "WOMPI_SANDBOX", "TARJETA (WOMPI)" -> "Tarjeta (Wompi)";
            default -> metodo;
        };
    }

    public boolean esContraentrega() {
        return "EFECTIVO_CONTRAENTREGA".equalsIgnoreCase(metodo) || "TRANSFERENCIA_CONTRAENTREGA".equalsIgnoreCase(metodo);
    }

    public boolean estaPagado() {
        return (estado != null && estado == 1) || "PAGADO".equalsIgnoreCase(estadoPago) || "APROBADO".equalsIgnoreCase(estadoPago);
    }
}
