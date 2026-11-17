package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.entity.*;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidos;

    @MockBean
    private UsuarioService usuarios;

    @MockBean
    private CarritoService carrito;

    private Usuario userOwner;
    private Usuario userOther;
    private Pedido pedidoTest;

    @BeforeEach
    void setUp() {
        userOwner = Usuario.builder()
                .id("user-123")
                .nombreCompleto("Juan Perez")
                .correo("juan@ejemplo.com")
                .build();

        userOther = Usuario.builder()
                .id("user-999")
                .nombreCompleto("Carlos Hacker")
                .correo("carlos@ejemplo.com")
                .build();

        Producto producto = new Producto();
        producto.setId("prod-1");
        producto.setNombre("Patacones con Hogao");
        producto.setPrecio(14000.0);
        producto.setEntidad(Usuario.builder().nombreCompleto("Restaurante Caribe").build());

        PedidoItem item = PedidoItem.builder()
                .producto(producto)
                .cantidad(2)
                .subtotal(28000.0)
                .build();

        Envio envio = Envio.builder()
                .dirreccion("Playa de Bocagrande")
                .descripcion("Frente al hotel, carpa azul")
                .mesa(5)
                .carpa(2)
                .build();

        Pago pago = Pago.builder()
                .id("pago-1")
                .metodo("EFECTIVO_CONTRAENTREGA")
                .estadoPago("PENDIENTE_CONTRAENTREGA")
                .estado(6)
                .valor(33000.0)
                .build();

        pedidoTest = Pedido.builder()
                .id("pedido-abc-123")
                .estado(0)
                .total(33000.0)
                .timestamp(LocalDateTime.of(2026, 11, 16, 23, 58))
                .cliente(userOwner)
                .envio(envio)
                .pago(pago)
                .carrito(List.of(item))
                .build();

        when(pedidos.construirDetalleDTO(any())).thenCallRealMethod();
        when(pedidos.formatearMoneda(any())).thenCallRealMethod();
    }

    @Test
    @DisplayName("GET /user/orders retorna 200 OK y la vista user-orders para el usuario autenticado")
    void mostrarMisPedidos_retorna200() throws Exception {
        when(usuarios.buscarUsuario("juan@ejemplo.com")).thenReturn(userOwner);
        when(pedidos.buscarPedidoConPaginaOrdenFiltro(any())).thenReturn(new PageImpl<>(List.of(pedidoTest)));
        when(carrito.listarCarrito("juan@ejemplo.com")).thenReturn(Collections.emptyList());

        Principal principal = () -> "juan@ejemplo.com";

        mockMvc.perform(get("/user/orders").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("user-orders"))
                .andExpect(model().attributeExists("pedidos"))
                .andExpect(model().attributeExists("nombreUsuario"));
    }

    @Test
    @DisplayName("GET /user/api/orders/{id} retorna 200 OK con JSON cuando el pedido pertenece al usuario")
    void obtenerDetallePedido_exitosoParaDuenio() throws Exception {
        when(usuarios.buscarUsuario("juan@ejemplo.com")).thenReturn(userOwner);
        when(pedidos.buscarPorId("pedido-abc-123")).thenReturn(Optional.of(pedidoTest));

        Principal principal = () -> "juan@ejemplo.com";

        mockMvc.perform(get("/user/api/orders/pedido-abc-123").principal(principal))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("pedido-abc-123"))
                .andExpect(jsonPath("$.total").value(33000.0))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].nombre").value("Patacones con Hogao"))
                .andExpect(jsonPath("$.items[0].vendedor").value("Restaurante Caribe"))
                .andExpect(jsonPath("$.direccionEntrega").value("Playa de Bocagrande"))
                .andExpect(jsonPath("$.estadoPagoNombre").value("Pendiente contraentrega"));
    }

    @Test
    @DisplayName("GET /user/api/orders/{id} retorna 403 Forbidden cuando el pedido pertenece a otro usuario")
    void obtenerDetallePedido_prohibidoParaOtroUsuario() throws Exception {
        when(usuarios.buscarUsuario("carlos@ejemplo.com")).thenReturn(userOther);
        when(pedidos.buscarPorId("pedido-abc-123")).thenReturn(Optional.of(pedidoTest));

        Principal principal = () -> "carlos@ejemplo.com";

        mockMvc.perform(get("/user/api/orders/pedido-abc-123").principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("Acceso denegado")));
    }

    @Test
    @DisplayName("GET /user/api/orders/{id} retorna 404 Not Found cuando el pedido no existe")
    void obtenerDetallePedido_noEncontrado() throws Exception {
        when(usuarios.buscarUsuario("juan@ejemplo.com")).thenReturn(userOwner);
        when(pedidos.buscarPorId("pedido-inexistente")).thenReturn(Optional.empty());

        Principal principal = () -> "juan@ejemplo.com";

        mockMvc.perform(get("/user/api/orders/pedido-inexistente").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString("no encontrado")));
    }
}
