package com.proyecto.PlayApp.Controller.Chat;

import com.proyecto.PlayApp.dto.RagAskRequest;
import com.proyecto.PlayApp.dto.RagAskResponse;
import com.proyecto.PlayApp.service.PlayAppRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat/rag")
@RequiredArgsConstructor
public class ChatRagController {
    private final PlayAppRagService playAppRagService;

    @PostMapping("/ask")
    public ResponseEntity<RagAskResponse> ask(@RequestBody RagAskRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(new RagAskResponse(
                    "La pregunta no puede estar vacia.",
                    List.of(),
                    List.of(),
                    false,
                    false
            ));
        }
        return ResponseEntity.ok(playAppRagService.ask(request.getQuestion()));
    }
}
