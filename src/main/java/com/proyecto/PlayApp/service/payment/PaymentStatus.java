package com.proyecto.PlayApp.service.payment;

public enum PaymentStatus {
    INICIADO(0, "Iniciado"),
    APROBADO(1, "Aprobado"),
    RECHAZADO(2, "Rechazado"),
    PENDIENTE(3, "Pendiente"),
    CANCELADO(4, "Cancelado"),
    ERROR(5, "Error");

    private final int codigo;
    private final String descripcion;

    PaymentStatus(int codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static PaymentStatus fromCodigo(Integer codigo) {
        if (codigo == null) return INICIADO;
        for (PaymentStatus s : values()) {
            if (s.codigo == codigo) return s;
        }
        return INICIADO;
    }
}
