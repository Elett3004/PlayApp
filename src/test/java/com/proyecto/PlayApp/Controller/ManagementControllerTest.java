package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.dto.PedidoDetalleDTO;
import com.proyecto.PlayApp.entity.Pago;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.service.*;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService servicio;

    @MockBean
    private PedidoService pedidos;

    @MockBean
    private ReporteService reportes;

    @MockBean
    private ModeloMatematicoService modeloMatematico;

    @MockBean
    private UsuarioService usuarios;

    @MockBean
    private CurrentWeatherService currentWeather;

    private Usuario adminGlobal;
    private Usuario adminRestaurante;
    private Pedido pedidoTest;

    @BeforeEach
    void setUp() {
        adminGlobal = Usuario.builder()
                .id("admin-global-id")
                .correo("adminplayapp01@gmail.com")
                .nombreCompleto("Administrador General")
                .rol("ADMIN")
                .build();

        adminRestaurante = Usuario.builder()
                .id("rest-1")
                .correo("restaurante1@playapp.com")
                .nombreCompleto("Restaurante Bocagrande")
                .rol("ADMIN")
                .build();

        Usuario cliente = Usuario.builder()
                .id("cli-1")
                .nombreCompleto("Maria Gomez")
                .correo("maria@gmail.com")
                .build();

        Pago pago = Pago.builder()
                .id("pago-1")
                .metodo("EFECTIVO_CONTRAENTREGA")
                .estado(1)
                .estadoPago("PAGADO")
                .valor(45000.0)
                .build();

        pedidoTest = Pedido.builder()
                .id("orden-123")
                .estado(1)
                .total(45000.0)
                .restaurantes(List.of("rest-1"))
                .cliente(cliente)
                .pago(pago)
                .carrito(Collections.emptyList())
                .build();

        when(pedidos.construirDetalleDTO(any())).thenCallRealMethod();
        when(pedidos.formatearMoneda(any())).thenCallRealMethod();
    }

    @Test
    @DisplayName("GET /manager/orders retorna 200 OK y la vista Management/orders")
    void mostrarPedidos_retorna200() throws Exception {
        when(pedidos.buscarPedidoPorRestaurate(any())).thenReturn(new PageImpl<>(List.of(pedidoTest)));

        Principal principal = () -> "adminplayapp01@gmail.com";

        mockMvc.perform(get("/manager/orders").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("Management/orders"))
                .andExpect(model().attributeExists("pedidos"));
    }

    @Test
    @DisplayName("GET /manager/api/orders/{id} retorna 200 OK con JSON cuando el admin global consulta la orden")
    void obtenerDetallePedidoAdmin_exitosoParaAdminGlobal() throws Exception {
        when(pedidos.buscarPorId("orden-123")).thenReturn(Optional.of(pedidoTest));
        when(reportes.esAdminGlobal("adminplayapp01@gmail.com")).thenReturn(true);

        Principal principal = () -> "adminplayapp01@gmail.com";

        mockMvc.perform(get("/manager/api/orders/orden-123").principal(principal))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("orden-123"))
                .andExpect(jsonPath("$.clienteNombre").value("Maria Gomez"))
                .andExpect(jsonPath("$.total").value(45000.0));
    }

    @Test
    @DisplayName("GET /manager/api/orders/{id} retorna 403 Forbidden cuando el admin del restaurante no es dueño de la orden")
    void obtenerDetallePedidoAdmin_prohibidoParaRestauranteAjeno() throws Exception {
        Usuario adminAjeno = Usuario.builder()
                .id("rest-otro")
                .correo("restaurante2@playapp.com")
                .build();

        when(pedidos.buscarPorId("orden-123")).thenReturn(Optional.of(pedidoTest));
        when(reportes.esAdminGlobal("restaurante2@playapp.com")).thenReturn(false);
        when(usuarios.buscarUsuario("restaurante2@playapp.com")).thenReturn(adminAjeno);

        Principal principal = () -> "restaurante2@playapp.com";

        mockMvc.perform(get("/manager/api/orders/orden-123").principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("No tienes permisos")));
    }

    @Test
    @DisplayName("GET /manager/api/orders/{id} retorna 404 Not Found cuando la orden no existe")
    void obtenerDetallePedidoAdmin_noEncontrada() throws Exception {
        when(pedidos.buscarPorId("orden-inexistente")).thenReturn(Optional.empty());

        Principal principal = () -> "adminplayapp01@gmail.com";

        mockMvc.perform(get("/manager/api/orders/orden-inexistente").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("no encontrado")));
    }
}
