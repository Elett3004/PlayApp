package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.dto.RagAskResponse;
import com.proyecto.PlayApp.model.RagDocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayAppRagService {
    private static final int TOP_K = 4;
    private static final double MIN_SCORE = 0.08;

    private final RagRetrievalService retrievalService;
    private final GeminiService geminiService;

    public RagAskResponse ask(String question) {
        List<RagDocumentChunk> chunks = retrieveRelevantChunks(question);
        if (chunks.isEmpty()) {
            return new RagAskResponse(
                    "No encontre informacion suficiente en la base de conocimiento de PlayApp para responder eso.",
                    List.of(),
                    List.of(),
                    true,
                    false
            );
        }

        String context = buildContext(chunks);
        String answer = generateAnswer(question, context);
        return new RagAskResponse(answer, buildSources(chunks), buildRetrievedChunks(chunks), true, true);
    }

    public boolean hasRelevantContext(String question) {
        return !retrieveRelevantChunks(question).isEmpty();
    }

    public String generateChatbotAnswer(String question) {
        RagAskResponse response = ask(question);
        return response.isContextFound() ? response.getAnswer() : null;
    }

    private List<RagDocumentChunk> retrieveRelevantChunks(String question) {
        return retrievalService.retrieve(question, TOP_K).stream()
                .filter(chunk -> chunk.getScore() >= MIN_SCORE)
                .toList();
    }

    private String generateAnswer(String question, String context) {
        String prompt = """
                Eres el asistente de PlayApp y respondes preguntas frecuentes usando RAG.
                Responde en espanol claro, amable y breve.
                Usa solo el contexto recuperado. No inventes rutas, precios, politicas ni disponibilidad.
                Si la respuesta no esta en el contexto, indica que no hay informacion suficiente.
                Cuando haya pasos, usa una lista corta.

                Contexto recuperado:
                """ + context + "\n\nPregunta del usuario:\n" + question;

        try {
            return geminiService.generateReply(prompt);
        } catch (Exception ex) {
            log.warn("Gemini no respondio para RAG. Se usara contexto local.", ex);
            return "Segun la base de conocimiento de PlayApp:\n\n" + context;
        }
    }

    private String buildContext(List<RagDocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (RagDocumentChunk chunk : chunks) {
            sb.append("Fuente: ").append(chunk.getSource()).append("\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<String> buildSources(List<RagDocumentChunk> chunks) {
        return chunks.stream()
                .map(RagDocumentChunk::getSource)
                .distinct()
                .toList();
    }

    private List<String> buildRetrievedChunks(List<RagDocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "[%s | score %.3f]\n%s".formatted(
                        chunk.getSource(),
                        chunk.getScore(),
                        chunk.getContent()
                ))
                .toList();
    }
}
