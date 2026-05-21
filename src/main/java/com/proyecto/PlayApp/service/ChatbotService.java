package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.dto.ChatAction;
import com.proyecto.PlayApp.dto.ChatHistoryResponse;
import com.proyecto.PlayApp.dto.ChatSendRequest;
import com.proyecto.PlayApp.dto.ChatSendResponse;
import com.proyecto.PlayApp.entity.ChatMessage;
import com.proyecto.PlayApp.entity.ChatSession;
import com.proyecto.PlayApp.repository.ChatMessageRepository;
import com.proyecto.PlayApp.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String FALLBACK_REPLY = "Puedo ayudarte con productos, precios, pedidos y pagos. Prueba por ejemplo: quiero algo refrescante, como compro o como pago.";
    private static final String GEMINI_UNAVAILABLE_REPLY = "En este momento estoy en modo asistente local. Igual puedo ayudarte con productos, pagos y pedidos.";
    private static final int CONTEXT_MESSAGES = 6;
    private static final int CONTEXT_USER_MESSAGES = 3;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GeminiService geminiService;
    private final ChatIntentService chatIntentService;

    public ChatSendResponse sendMessage(ChatSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio");
        }
        String sanitizedMessage = sanitizeMessage(request.getMessage());
        validateRequest(sanitizedMessage);
        String effectiveUserId = resolveEffectiveUserId(request.getUserId());

        ChatSession session = resolveSession(request.getSessionId(), effectiveUserId);
        List<ChatMessage> historyBeforeMessage = chatMessageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
        List<String> recentUserMessages = extractRecentUserMessages(historyBeforeMessage);

        LocalDateTime now = LocalDateTime.now();
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(sanitizedMessage);
        userMessage.setTimestamp(now);
        chatMessageRepository.save(userMessage);

        ChatIntentService.IntentResolution intentResolution = chatIntentService.resolve(
                userMessage.getContent(),
                effectiveUserId,
                recentUserMessages
        );

        AssistantReply assistantReply = resolveAssistantReply(intentResolution, session.getId(), sanitizedMessage);
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(assistantReply.message());
        assistantMessage.setTimestamp(LocalDateTime.now());
        assistantMessage.setMetadata(buildAssistantMetadata(intentResolution.intent(), assistantReply.actions()));
        chatMessageRepository.save(assistantMessage);

        session.setLastMessageAt(assistantMessage.getTimestamp());
        chatSessionRepository.save(session);

        return new ChatSendResponse(
                session.getId(),
                assistantMessage.getContent(),
                assistantMessage.getTimestamp(),
                assistantReply.actions()
        );
    }

    public ChatHistoryResponse getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId es obligatorio");
        }

        String effectiveUserId = resolveEffectiveUserId(null);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Sesion de chat no encontrada"));
        if (!canAccessSession(session, effectiveUserId)) {
            throw new NoSuchElementException("Sesion de chat no encontrada");
        }

        List<ChatHistoryResponse.ChatHistoryMessage> messages = chatMessageRepository
                .findBySessionIdOrderByTimestampAsc(sessionId)
                .stream()
                .map(item -> new ChatHistoryResponse.ChatHistoryMessage(
                        item.getRole(),
                        item.getContent(),
                        item.getTimestamp(),
                        extractActionsFromMetadata(item.getMetadata())))
                .toList();

        return new ChatHistoryResponse(sessionId, messages);
    }

    private ChatSession resolveSession(String sessionId, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession existing = chatSessionRepository.findById(sessionId).orElse(null);
            if (existing != null) {
                if (!canAccessSession(existing, userId)) {
                    return createSession(userId);
                }
                if ((existing.getUserId() == null || existing.getUserId().isBlank())
                        && userId != null && !userId.isBlank()) {
                    existing.setUserId(userId.trim());
                }
                return chatSessionRepository.save(existing);
            }
        }

        return createSession(userId);
    }

    private ChatSession createSession(String userId) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId == null || userId.isBlank() ? null : userId.trim());
        session.setCreatedAt(now);
        session.setLastMessageAt(now);
        session.setStatus(DEFAULT_STATUS);
        return chatSessionRepository.save(session);
    }

    private boolean canAccessSession(ChatSession session, String userId) {
        String owner = session.getUserId();
        if (owner == null || owner.isBlank()) {
            return userId == null || userId.isBlank();
        }
        return owner.equalsIgnoreCase(userId == null ? "" : userId.trim());
    }

    private String resolveEffectiveUserId(String requestUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !authentication.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return authentication.getName().trim().toLowerCase(Locale.ROOT);
        }

        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId.trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private void validateRequest(String sanitizedMessage) {
        if (sanitizedMessage == null || sanitizedMessage.isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (sanitizedMessage.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("El mensaje supera el maximo permitido de " + MAX_MESSAGE_LENGTH + " caracteres");
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("<[^>]*>", "")
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "")
                .replace("<", "")
                .replace(">", "")
                .trim();
    }

    private AssistantReply resolveAssistantReply(ChatIntentService.IntentResolution intentResolution, String sessionId, String userMessage) {
        if (intentResolution.handled()) {
            String response = intentResolution.response();
            if (intentResolution.preferNaturalResponse() && intentResolution.modelContext() != null && !intentResolution.modelContext().isBlank()) {
                response = generateGroundedReply(
                        userMessage,
                        intentResolution.modelContext(),
                        intentResolution.response()
                );
            }
            return new AssistantReply(response, safeActions(intentResolution.actions()));
        }
        String context = buildShortContext(sessionId);
        String reply = generateAssistantReply(userMessage, context);
        return new AssistantReply(reply, List.of(ChatAction.link("Ir a tienda", "/shop")));
    }

    private String generateAssistantReply(String userMessage, String context) {
        String prompt = buildGeneralAssistantPrompt(userMessage, context);
        try {
            return geminiService.generateReply(prompt);
        } catch (Exception ex) {
            log.warn("Fallo al generar respuesta con Gemini. Se devolvera fallback.", ex);
            return buildLocalFallbackReply(userMessage, ex.getMessage());
        }
    }

    private String generateGroundedReply(String userMessage, String factualContext, String fallbackResponse) {
        String prompt = """
                Eres el asistente de PlayApp.
                Responde en espanol claro, breve y amable.
                Regla critica: usa SOLO la informacion del contexto factual. No inventes productos, precios ni disponibilidad.
                Si el usuario pide compra o pago, explica pasos concretos y ordenados.
                Si el contexto no alcanza para responder algo, dilo explicitamente.
                Devuelve listas cortas cuando aplique.

                Contexto factual:
                """ + factualContext + "\n\nMensaje del usuario:\n" + userMessage;
        try {
            return geminiService.generateReply(prompt);
        } catch (Exception ex) {
            log.warn("Fallo respuesta natural con contexto factual. Se usa fallback local.", ex);
            return fallbackResponse;
        }
    }

    private String buildGeneralAssistantPrompt(String userMessage, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres el asistente de PlayApp. ")
                .append("Responde en espanol, de forma breve, natural y util. ")
                .append("No inventes productos, precios ni estados de pedidos.\n")
                .append("Si piden comprar o pagar, explica un paso a paso practico.\n")
                .append("Si no hay datos suficientes, dilo con transparencia.\n\n");

        if (context != null && !context.isBlank()) {
            prompt.append("Contexto breve del chat:\n")
                    .append(context)
                    .append("\n\n");
        }

        prompt.append("Mensaje actual del usuario:\n")
                .append(userMessage);
        return prompt.toString();
    }

    private String buildShortContext(String sessionId) {
        List<ChatMessage> fullHistory = chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int startIndex = Math.max(0, fullHistory.size() - CONTEXT_MESSAGES);
        List<ChatMessage> recent = fullHistory.subList(startIndex, fullHistory.size());

        return recent.stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));
    }

    private List<String> extractRecentUserMessages(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<String> userMessages = history.stream()
                .filter(item -> ROLE_USER.equalsIgnoreCase(item.getRole()))
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();

        if (userMessages.size() <= CONTEXT_USER_MESSAGES) {
            return userMessages;
        }
        return userMessages.subList(userMessages.size() - CONTEXT_USER_MESSAGES, userMessages.size());
    }

    private String buildLocalFallbackReply(String userMessage, String errorMessage) {
        String normalized = normalize(userMessage);
        if (containsAny(normalized, "hola", "buenas", "buenos dias", "buenas tardes", "buenas noches")) {
            return GEMINI_UNAVAILABLE_REPLY + " Dime que necesitas y te guio paso a paso.";
        }
        if (containsAny(normalized, "gracias", "ok", "vale", "listo")) {
            return "Con gusto. Si quieres, ahora te puedo ayudar con recomendaciones, precios o proceso de compra.";
        }
        if (containsAny(normalized, "ayuda", "help", "soporte", "no se")) {
            return "Claro. Te puedo ayudar en 4 frentes: 1) recomendaciones 2) precios 3) compras/pagos 4) estado de pedidos.";
        }
        if (errorMessage != null && errorMessage.contains("GEMINI_API_KEY")) {
            return GEMINI_UNAVAILABLE_REPLY + " Puedes hacer consultas concretas como: que recomiendas para el calor, como pago o cuanto cuesta pescado.";
        }
        return FALLBACK_REPLY;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String message, String... values) {
        for (String item : values) {
            if (message.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildAssistantMetadata(String intent, List<ChatAction> actions) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (intent != null && !intent.isBlank()) {
            metadata.put("intent", intent);
        }
        List<ChatAction> safeActions = safeActions(actions);
        if (!safeActions.isEmpty()) {
            List<Map<String, Object>> actionMaps = safeActions.stream()
                    .map(action -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("type", action.getType());
                        map.put("label", action.getLabel());
                        map.put("url", action.getUrl());
                        return map;
                    })
                    .toList();
            metadata.put("actions", actionMaps);
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private List<ChatAction> extractActionsFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return List.of();
        }
        Object actionsValue = metadata.get("actions");
        if (!(actionsValue instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        List<ChatAction> actions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> actionMap)) {
                continue;
            }
            Object type = actionMap.get("type");
            Object label = actionMap.get("label");
            Object url = actionMap.get("url");
            if (!(label instanceof String labelValue) || labelValue.isBlank()) {
                continue;
            }
            if (!(url instanceof String urlValue) || urlValue.isBlank()) {
                continue;
            }
            String typeValue = type instanceof String ? (String) type : "link";
            actions.add(new ChatAction(typeValue, labelValue, urlValue));
        }
        return actions;
    }

    private List<ChatAction> safeActions(List<ChatAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream()
                .filter(action -> action != null
                        && action.getLabel() != null
                        && !action.getLabel().isBlank()
                        && action.getUrl() != null
                        && !action.getUrl().isBlank())
                .limit(4)
                .toList();
    }

    private record AssistantReply(String message, List<ChatAction> actions) {
    }
}
