package com.proyecto.PlayApp.Controller;

import java.util.Collections;
import java.util.Optional;

import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.entity.Envio;
import com.proyecto.PlayApp.entity.Pago;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.repository.PedidoRepository;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.UsuarioService;
import com.proyecto.PlayApp.service.payment.PaymentGatewayManager;
import com.proyecto.PlayApp.service.payment.PaymentInitResponse;

@WebMvcTest(PagoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PagoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CarritoService carritos;

    @MockBean
    private PedidoService pedidos;

    @MockBean
    private PedidoRepository pedidoRepository;

    @MockBean
    private PaymentGatewayManager paymentManager;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private UsuarioService usuarioService;

    private Pedido crearPedidoEjemplo() {
        Pedido pedido = new Pedido();
        pedido.setId("orden-123");
        pedido.setTotal(84000.0);

        Usuario cliente = new Usuario();
        cliente.setCorreo("test@playapp.com");
        pedido.setCliente(cliente);

        Envio envio = new Envio();
        envio.setDirreccion("Playa de Bocagrande, Cartagena");
        envio.setMesa(2);
        pedido.setEnvio(envio);

        Pago pago = new Pago();
        pago.setMetodo("Tarjeta (Wompi)");
        pedido.setPago(pago);

        pedido.setCarrito(Collections.emptyList());
        return pedido;
    }

    @Test
    void proceedAprobado_debeRenderizarConfirmacionConExito() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/payment/proceed")
                        .param("estado", "APROBADO")
                        .param("orden", "orden-123")
                        .param("metodo", "WOMPI_SANDBOX"))
                .andExpect(status().isOk())
                .andExpect(view().name("pedido-confirmacion"))
                .andExpect(model().attribute("success", containsString("¡Pago aprobado!")))
                .andExpect(model().attribute("puedeReintentar", false))
                .andExpect(content().string(containsString("¡Pago Aprobado con Éxito!")))
                .andExpect(content().string(containsString("#orden-123")))
                .andExpect(content().string(containsString("Playa de Bocagrande, Cartagena")))
                .andExpect(content().string(containsString("Ver mis pedidos")));
    }

    @Test
    void proceedRechazado_debeRenderizarErrorYPermitirReintentar() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/payment/proceed")
                        .param("estado", "RECHAZADO")
                        .param("orden", "orden-123")
                        .param("metodo", "Tarjeta (Wompi)"))
                .andExpect(status().isOk())
                .andExpect(view().name("pedido-confirmacion"))
                .andExpect(model().attribute("error", containsString("Tu pago fue rechazado")))
                .andExpect(model().attribute("puedeReintentar", true))
                .andExpect(model().attribute("ordenReintentar", "orden-123"))
                .andExpect(content().string(containsString("Transacción no completada")))
                .andExpect(content().string(containsString("Intentar nuevamente")));
    }

    @Test
    void proceedCancelado_debeMostrarMensajeCancelado() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/payment/proceed")
                        .param("estado", "CANCELADO")
                        .param("orden", "orden-123")
                        .param("metodo", "Tarjeta (Wompi)"))
                .andExpect(status().isOk())
                .andExpect(view().name("pedido-confirmacion"))
                .andExpect(model().attribute("puedeReintentar", true))
                .andExpect(content().string(containsString("cancelado")));
    }

    @Test
    void proceedContraentrega_efectivo_debeMostrarPedidoConfirmadoYAvisoPagoPendiente() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        pedido.getPago().setMetodo("EFECTIVO_CONTRAENTREGA");
        pedido.getPago().setEstado(6);
        pedido.getPago().setEstadoPago("PENDIENTE_CONTRAENTREGA");
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/payment/proceed")
                        .param("estado", "CONTRAENTREGA")
                        .param("orden", "orden-123")
                        .param("metodo", "EFECTIVO_CONTRAENTREGA"))
                .andExpect(status().isOk())
                .andExpect(view().name("pedido-confirmacion"))
                .andExpect(model().attribute("contraentrega", true))
                .andExpect(content().string(containsString("¡Pedido confirmado!")))
                .andExpect(content().string(containsString("Pago pendiente contraentrega: paga al recibir tu pedido.")))
                .andExpect(content().string(containsString("Efectivo contraentrega")))
                .andExpect(content().string(containsString("Pendiente contraentrega")));
    }

    @Test
    void proceedContraentrega_transferencia_debeMostrarAvisoPagoPendiente() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        pedido.getPago().setMetodo("TRANSFERENCIA_CONTRAENTREGA");
        pedido.getPago().setEstado(6);
        pedido.getPago().setEstadoPago("PENDIENTE_CONTRAENTREGA");
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/payment/proceed")
                        .param("estado", "CONTRAENTREGA")
                        .param("orden", "orden-123")
                        .param("metodo", "TRANSFERENCIA_CONTRAENTREGA"))
                .andExpect(status().isOk())
                .andExpect(view().name("pedido-confirmacion"))
                .andExpect(model().attribute("contraentrega", true))
                .andExpect(content().string(containsString("¡Pedido confirmado!")))
                .andExpect(content().string(containsString("Pago pendiente contraentrega: paga al recibir tu pedido.")))
                .andExpect(content().string(containsString("Transferencia contraentrega")));
    }

    @Test
    void paymentGateway_debeSerLimpiaSinElementosDeDesarrolloVisibles() throws Exception {
        Pedido pedido = crearPedidoEjemplo();
        when(pedidoRepository.findById("orden-123")).thenReturn(Optional.of(pedido));

        PaymentInitResponse initResponse = PaymentInitResponse.builder()
                .referencia("REF-orden-123")
                .montoEnCentavos(8400000L)
                .moneda("COP")
                .configurado(false)
                .build();

        when(paymentManager.prepararPago(any(), anyDouble(), anyString())).thenReturn(initResponse);

        mockMvc.perform(get("/payment/paymentgateway")
                        .param("orden", "orden-123")
                        .param("valor", "84000"))
                .andExpect(status().isOk())
                .andExpect(view().name("fake-payment-gateway"))
                .andExpect(content().string(containsString("Pago Seguro con Tarjeta")))
                .andExpect(content().string(containsString("Resumen del Pedido")))
                .andExpect(content().string(containsString("orden-123")))
                .andExpect(content().string(not(containsString("Modo Sandbox Demo Activo"))))
                .andExpect(content().string(not(containsString("Tarjetas de Prueba para Demostración"))))
                .andExpect(content().string(not(containsString("Tarjeta Aprobada (4242)"))))
                .andExpect(content().string(not(containsString("Tarjeta Rechazada (4111)"))))
                .andExpect(content().string(not(containsString("Simulador Wompi"))));
    }
}
