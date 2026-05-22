package com.proyecto.PlayApp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GeminiService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final List<String> PREFERRED_MODELS = List.of(
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
    );

    private final RestTemplate restTemplate;
    private volatile String activeModel;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-flash-latest}")
    private String model;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    public GeminiService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }

    public String generateReply(String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY no configurada");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("El mensaje del usuario es obligatorio");
        }

        String requestedModel = normalizeModelName(model);
        List<String> candidateModels;
        try {
            candidateModels = resolveCandidateModels(requestedModel);
        } catch (HttpStatusCodeException ex) {
            throwForGeminiPermissionErrors(ex);
            throw new IllegalStateException("Error invocando Gemini", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Error invocando Gemini", ex);
        }

        if (candidateModels.isEmpty()) {
            throw new IllegalStateException("No hay modelos Gemini compatibles con generateContent");
        }

        RuntimeException lastFailure = null;
        for (String candidateModel : candidateModels) {
            try {
                String reply = callGenerateContent(candidateModel, userMessage);
                activeModel = candidateModel;
                return reply;
            } catch (HttpStatusCodeException ex) {
                throwForGeminiPermissionErrors(ex);
                lastFailure = new IllegalStateException("Error invocando Gemini con el modelo " + candidateModel, ex);
                if (!isRecoverableModelError(ex)) {
                    throw lastFailure;
                }
            } catch (RestClientException ex) {
                throw new IllegalStateException("Error invocando Gemini", ex);
            }
        }

        throw lastFailure == null
                ? new IllegalStateException("No fue posible invocar Gemini")
                : lastFailure;
    }

    private String callGenerateContent(String modelName, String userMessage) {
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(baseUrl))
                .pathSegment("models", normalizeModelName(modelName) + ":generateContent")
                .build()
                .toUri();

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, request, Map.class);
        return extractText(response.getBody());
    }

    private String extractText(Map body) {
        if (body == null) {
            throw new IllegalStateException("Respuesta vacia de Gemini");
        }

        Object candidatesObj = body.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini no devolvio candidatos");
        }

        Object firstObj = candidates.getFirst();
        if (!(firstObj instanceof Map<?, ?> firstCandidate)) {
            throw new IllegalStateException("Formato de respuesta invalido (candidate)");
        }

        Object contentObj = firstCandidate.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            throw new IllegalStateException("Formato de respuesta invalido (content)");
        }

        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
            throw new IllegalStateException("Formato de respuesta invalido (parts)");
        }

        Object firstPartObj = parts.getFirst();
        if (!(firstPartObj instanceof Map<?, ?> firstPart)) {
            throw new IllegalStateException("Formato de respuesta invalido (part)");
        }

        Object textObj = firstPart.get("text");
        if (!(textObj instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Gemini devolvio texto vacio");
        }

        return text.trim();
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeModelName(String rawModelName) {
        if (rawModelName == null || rawModelName.isBlank()) {
            return "gemini-2.0-flash";
        }
        String clean = rawModelName.trim();
        if (clean.startsWith("models/")) {
            return clean.substring("models/".length());
        }
        return clean;
    }

    private boolean isRecoverableModelError(HttpStatusCodeException ex) {
        HttpStatusCode code = ex.getStatusCode();
        if (code.value() == 429) {
            return true;
        }
        if (code.value() == 500 || code.value() == 502 || code.value() == 503 || code.value() == 504) {
            return true;
        }
        String body = ex.getResponseBodyAsString();
        if (code.value() == 404) {
            return body != null && body.contains("models/");
        }
        return code.value() == 400
                && body != null
                && body.toLowerCase().contains("model");
    }

    private List<String> resolveCandidateModels(String requestedModel) {
        Set<String> candidateModels = new LinkedHashSet<>();
        if (activeModel != null && !activeModel.isBlank()) {
            candidateModels.add(normalizeModelName(activeModel));
        }
        candidateModels.add(normalizeModelName(requestedModel));

        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(baseUrl))
                .pathSegment("models")
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, request, Map.class);
        Map body = response.getBody();
        if (body == null) {
            return new ArrayList<>(candidateModels);
        }

        Object modelsObj = body.get("models");
        if (!(modelsObj instanceof List<?> models) || models.isEmpty()) {
            return new ArrayList<>(candidateModels);
        }

        List<String> compatibleModels = new ArrayList<>();
        for (Object item : models) {
            if (!(item instanceof Map<?, ?> modelMap)) {
                continue;
            }
            Object methodsObj = modelMap.get("supportedGenerationMethods");
            Object nameObj = modelMap.get("name");
            if (!(nameObj instanceof String name) || name.isBlank()) {
                continue;
            }
            if (methodsObj instanceof List<?> methods
                    && methods.stream().filter(Objects::nonNull).map(Object::toString).anyMatch("generateContent"::equals)) {
                compatibleModels.add(normalizeModelName(name));
            }
        }

        if (compatibleModels.isEmpty()) {
            return new ArrayList<>(candidateModels);
        }

        for (String preferred : PREFERRED_MODELS) {
            if (compatibleModels.contains(preferred)) {
                candidateModels.add(preferred);
            }
        }

        candidateModels.addAll(compatibleModels.stream().sorted().collect(Collectors.toList()));
        return new ArrayList<>(candidateModels);
    }

    private void throwForGeminiPermissionErrors(HttpStatusCodeException ex) {
        if (ex.getStatusCode().value() != 403) {
            return;
        }
        String body = ex.getResponseBodyAsString();
        if (body != null && body.toLowerCase().contains("leaked")) {
            throw new IllegalStateException("GEMINI_API_KEY fue bloqueada por Google. Genera una nueva key y reemplazala en config/application-secrets.env", ex);
        }
        throw new IllegalStateException("Gemini rechazo la solicitud por permisos. Revisa GEMINI_API_KEY y restricciones de la clave.", ex);
    }
}
