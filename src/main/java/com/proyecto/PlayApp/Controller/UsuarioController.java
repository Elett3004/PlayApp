package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.dto.BusquedaDTO;
import com.proyecto.PlayApp.dto.PedidoDetalleDTO;
import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.entity.PedidoItem;
import com.proyecto.PlayApp.entity.Producto;
import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.service.CarritoService;
import com.proyecto.PlayApp.service.PedidoService;
import com.proyecto.PlayApp.service.UsuarioService;
import com.proyecto.PlayApp.util.PaginationMaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user")
public class UsuarioController {
    private final PedidoService pedidos;
    private final UsuarioService usuarios;
    private final CarritoService carrito;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale LOCALE_CO = new Locale("es", "CO");

    @GetMapping("/orders")
    public String mostrarMisPedidos(
            Principal usuario,
            Model model
    ){
        Usuario loggedUser = usuarios.buscarUsuario(usuario.getName());
        Page<Pedido> items = pedidos.buscarPedidoConPaginaOrdenFiltro(
                BusquedaDTO.builder().page(0).size(5)
                        .id(loggedUser.getId())
                        .sort("[{\"campo\":\"timestamp\",\"direccion\":\"desc\"}]")
                        .build()
        );

        model.addAttribute("carrito", numeroItemsCarrito(usuario));
        model.addAttribute("paginas", new PaginationMaker().makePages(items));
        model.addAttribute("pedidos", items);
        model.addAttribute("estadoActual", "");

        String nombreusuario = "";
        if(usuario != null){
            nombreusuario = usuarios.buscarUsuario(usuario.getName()).getNombreCompleto();
        }

        model.addAttribute("nombreUsuario", nombreusuario);
        return "user-orders";
    }

    @GetMapping("/search")
    public String buscarProducto(
            Principal usuario,
            Model model,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "5") Integer size,
            @RequestParam(name = "sort", defaultValue = "[{\"campo\":\"timestamp\",\"direccion\":\"desc\"}]") String sort,
            @RequestParam(name = "estado", required = false) Integer estado
    ) {
        Usuario loggedUser = usuarios.buscarUsuario(usuario.getName());
        Page<Pedido> items = pedidos.buscarPedidoConPaginaOrdenFiltro(
                BusquedaDTO.builder()
                        .id(loggedUser.getId())
                        .categoria(estado)
                        .page(page)
                        .size(size)
                        .sort(sort)
                        .build()
        );
        model.addAttribute("carrito", numeroItemsCarrito(usuario));
        model.addAttribute("paginas", new PaginationMaker().makePages(items));
        model.addAttribute("pedidos", items);
        model.addAttribute("estadoActual", estado != null ? estado.toString() : "");

        String nombreusuario = "";
        if(usuario != null){
            nombreusuario = loggedUser != null ? loggedUser.getNombreCompleto() : "";
        }
        model.addAttribute("nombreUsuario", nombreusuario);
        return "user-orders";
    }

    @GetMapping("/api/orders/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallePedido(@PathVariable String id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Debes iniciar sesión para consultar este pedido."));
        }

        Optional<Pedido> pedidoOpt = pedidos.buscarPorId(id);
        if (pedidoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pedido no encontrado."));
        }

        Pedido pedido = pedidoOpt.get();
        Usuario loggedUser = usuarios.buscarUsuario(principal.getName());
        Usuario cliente = pedido.getCliente();

        boolean esDuenio = false;
        if (cliente != null) {
            if (cliente.getCorreo() != null && cliente.getCorreo().equalsIgnoreCase(principal.getName())) {
                esDuenio = true;
            } else if (loggedUser != null && cliente.getId() != null && cliente.getId().equals(loggedUser.getId())) {
                esDuenio = true;
            }
        }

        if (!esDuenio) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado: este pedido no pertenece a tu cuenta."));
        }

        PedidoDetalleDTO dto = pedidos.construirDetalleDTO(pedido);
        return ResponseEntity.ok(dto);
    }

    private int numeroItemsCarrito(Principal user){
        if(user == null){
            return 0;
        }
        return carrito.listarCarrito(user.getName()).size();
    }
}
