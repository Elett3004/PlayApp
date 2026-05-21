package com.proyecto.PlayApp.Controller;

import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice(assignableTypes = ManagementController.class)
@RequiredArgsConstructor
public class ManagementModelAdvice {

    private final UsuarioService usuarios;

    @ModelAttribute
    public void addAdminDisplayName(Model model, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return;
        }

        String correo = principal.getName();
        Usuario usuario = usuarios.buscarUsuario(correo);
        String nombre = usuario != null ? usuario.getNombreCompleto() : null;

        if (nombre == null || nombre.isBlank()) {
            int atIndex = correo.indexOf('@');
            nombre = atIndex > 0 ? correo.substring(0, atIndex) : correo;
        }

        model.addAttribute("nombreAdmin", nombre);
    }
}
