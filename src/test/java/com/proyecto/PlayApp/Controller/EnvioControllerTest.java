package com.proyecto.PlayApp.Controller;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proyecto.PlayApp.dto.CompraDTO;
import com.proyecto.PlayApp.entity.CarritoItem;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.UsuarioService;

@WebMvcTest(EnvioController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnvioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidos;

    @MockBean
    private CarritoService carritos;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private UsuarioService usuarioService;

    private List<CarritoItem> carritoConItems() {
        CarritoItem item = new CarritoItem();
        item.setProductoId("prod-1");
        item.setCantidad(2);
        item.setPrecio(20000.0);
        return List.of(item);
    }

    @Test
    void proceedEfectivoContraentrega_debeRedirigirAConfirmacionDirectaSinPasarela() throws Exception {
        Principal principal = () -> "usuario@playapp.com";
        when(carritos.listarCarrito("usuario@playapp.com")).thenReturn(carritoConItems());

        Pedido pedidoCreado = new Pedido();
        pedidoCreado.setId("orden-efectivo-123");
        pedidoCreado.setTotal(45000.0);
        when(pedidos.RealizarPedido(any(CompraDTO.class))).thenReturn(pedidoCreado);

        mockMvc.perform(post("/shipment/proceed")
                        .principal(principal)
                        .param("direccion", "Playa de Bocagrande")
                        .param("zona", "Bocagrande")
                        .param("metodoPago", "EFECTIVO_CONTRAENTREGA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payment/proceed?estado=CONTRAENTREGA&orden=orden-efectivo-123&metodo=EFECTIVO_CONTRAENTREGA"));

        verify(carritos).limpiar("usuario@playapp.com");
    }

    @Test
    void proceedTransferenciaContraentrega_debeRedirigirAConfirmacionDirectaSinPasarela() throws Exception {
        Principal principal = () -> "usuario@playapp.com";
        when(carritos.listarCarrito("usuario@playapp.com")).thenReturn(carritoConItems());

        Pedido pedidoCreado = new Pedido();
        pedidoCreado.setId("orden-transf-456");
        pedidoCreado.setTotal(45000.0);
        when(pedidos.RealizarPedido(any(CompraDTO.class))).thenReturn(pedidoCreado);

        mockMvc.perform(post("/shipment/proceed")
                        .principal(principal)
                        .param("direccion", "Playa de Castillogrande")
                        .param("zona", "Castillogrande")
                        .param("metodoPago", "TRANSFERENCIA_CONTRAENTREGA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payment/proceed?estado=CONTRAENTREGA&orden=orden-transf-456&metodo=TRANSFERENCIA_CONTRAENTREGA"));

        verify(carritos).limpiar("usuario@playapp.com");
    }

    @Test
    void proceedWompi_debeRedirigirAPasarelaWompi() throws Exception {
        Principal principal = () -> "usuario@playapp.com";
        when(carritos.listarCarrito("usuario@playapp.com")).thenReturn(carritoConItems());

        Pedido pedidoCreado = new Pedido();
        pedidoCreado.setId("orden-wompi-789");
        pedidoCreado.setTotal(45000.0);
        when(pedidos.RealizarPedido(any(CompraDTO.class))).thenReturn(pedidoCreado);

        mockMvc.perform(post("/shipment/proceed")
                        .principal(principal)
                        .param("direccion", "Playa El Laguito")
                        .param("zona", "El Laguito")
                        .param("metodoPago", "WOMPI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payment/paymentgateway?valor=45000.0&metodo=WOMPI&orden=orden-wompi-789"));
    }
}
