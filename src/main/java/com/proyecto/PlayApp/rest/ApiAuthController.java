package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiAuthController {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String correo = credentials.get("correo");
        if (correo == null || correo.isBlank()) {
            correo = credentials.get("username");
        }
        String password = credentials.get("password");

        if (correo == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Correo y contraseña requeridos"));
        }

        Usuario usuario = usuarioRepository.findUsuarioByCorreo(correo.trim());
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", usuario.getId());
        userData.put("nombreCompleto", usuario.getNombreCompleto());
        userData.put("correo", usuario.getCorreo());
        userData.put("rol", usuario.getRol() != null ? usuario.getRol() : "ROLE_USER");
        boolean esAdmin = "ROLE_ADMIN".equalsIgnoreCase(usuario.getRol());
        userData.put("esAdmin", esAdmin);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "usuario", userData,
                "mensaje", "Inicio de sesión exitoso"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> data) {
        String nombre = data.get("nombreCompleto");
        String correo = data.get("correo");
        if (correo == null || correo.isBlank()) {
            correo = data.get("email");
        }
        String password = data.get("password");

        if (nombre == null || correo == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Todos los campos son obligatorios"));
        }

        try {
            Usuario nuevo = Usuario.builder()
                    .nombreCompleto(nombre.trim())
                    .correo(correo.trim().toLowerCase())
                    .password(password)
                    .build();

            Usuario creado = usuarioService.crearUsuario(nuevo);

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", creado.getId());
            userData.put("nombreCompleto", creado.getNombreCompleto());
            userData.put("correo", creado.getCorreo());
            userData.put("rol", creado.getRol());
            userData.put("esAdmin", false);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "usuario", userData,
                    "mensaje", "Usuario registrado exitosamente"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear la cuenta: " + e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(@RequestParam(name = "correo") String correo) {
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Correo no proporcionado"));
        }
        Usuario usuario = usuarioRepository.findUsuarioByCorreo(correo.trim());
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", usuario.getId());
        userData.put("nombreCompleto", usuario.getNombreCompleto());
        userData.put("correo", usuario.getCorreo());
        userData.put("rol", usuario.getRol());
        userData.put("esAdmin", "ROLE_ADMIN".equalsIgnoreCase(usuario.getRol()));

        return ResponseEntity.ok(userData);
    }
}
