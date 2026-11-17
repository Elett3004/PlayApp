package com.proyecto.PlayApp.rest;

import com.proyecto.PlayApp.entity.Resena;
import com.proyecto.PlayApp.repository.ResenaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiReviewController {

    private final ResenaRepository resenaRepository;

    @GetMapping("/reviews")
    public ResponseEntity<List<Resena>> listarResenas() {
        List<Resena> lista = resenaRepository.findAllByOrderByFechaDesc();
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/reviews")
    public ResponseEntity<?> crearResena(@RequestBody Map<String, Object> body) {
        String nombre = (String) body.get("nombre");
        String comentario = (String) body.get("comentario");
        Integer valoracion = body.get("valoracion") != null ? ((Number) body.get("valoracion")).intValue() : 5;

        if (nombre == null || nombre.isBlank() || comentario == null || comentario.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre y comentario son obligatorios"));
        }

        Resena resena = new Resena();
        resena.setNombre(nombre.trim());
        resena.setComentario(comentario.trim());
        resena.setValoracion(Math.max(1, Math.min(5, valoracion)));
        resena.setFecha(new java.util.Date());

        Resena guardada = resenaRepository.save(resena);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    @PostMapping("/contact")
    public ResponseEntity<?> enviarMensajeContacto(@RequestBody Map<String, String> body) {
        String nombre = body.get("nombre");
        String email = body.get("email");
        String asunto = body.get("asunto");
        String mensaje = body.get("mensaje");

        if (nombre == null || email == null || mensaje == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre, correo y mensaje son requeridos"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mensaje", "Tu mensaje ha sido recibido por el equipo de PlayApp Cartagena. Te contactaremos pronto."
        ));
    }
}
