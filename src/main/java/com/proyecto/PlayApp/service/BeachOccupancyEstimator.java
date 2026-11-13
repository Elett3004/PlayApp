package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Percentage ranges approved in docs/Tabla_porcentajes_ocupacion.xlsx. */
@Service
public class BeachOccupancyEstimator {
    public record Estimate(Integer minimum, Integer maximum, String condition, String explanation, String summary) {}
    public record Rule(String id, String condition, String criterion, Integer minimum, Integer maximum, String origin) {}
    private final List<Rule> rules;
    private final Map<String, Rule> byId;

    public BeachOccupancyEstimator() {
        try (var input = getClass().getResourceAsStream("/occupancy/rules.json")) {
            if (input == null) throw new IllegalStateException("Falta la tabla de ocupación.");
            rules = List.copyOf(new ObjectMapper().readValue(input, new TypeReference<List<Rule>>() {}));
            byId = rules.stream().collect(Collectors.toUnmodifiableMap(Rule::id, r -> r));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la tabla de ocupación.", ex);
        }
    }
    public List<Rule> rules() { return rules; }

    public Estimate estimate(List<Integer> codes, double feelsLike, double windKmh, Double visibilityMeters, boolean daylight) {
        if (codes == null || codes.isEmpty() || !Double.isFinite(feelsLike) || !Double.isFinite(windKmh) || windKmh < 0) {
            return unavailable("missing", "No hay datos meteorológicos suficientes para estimar la ocupación actual.");
        }
        if (!daylight) {
            Rule night = byId.get("night");
            String summary = "Durante la noche se espera una afluencia muy baja, aunque puede haber algunas personas en la playa.";
            return new Estimate(night.minimum(), night.maximum(), night.condition(),
                    "La ocupación nocturna podría situarse entre el " + night.minimum() + " % y el " + night.maximum()
                            + " %. " + summary + " Es una aproximación orientativa, no un conteo de visitantes presentes.", summary);
        }
        boolean mist = codes.stream().anyMatch(c -> Set.of(701, 721, 741).contains(c));
        boolean visibilityValid = visibilityMeters != null && Double.isFinite(visibilityMeters) && visibilityMeters >= 0;
        if (mist && !visibilityValid) {
            return unavailable("missing", "La API reporta bruma o niebla, pero no informa una visibilidad válida para elegir el rango.");
        }
        Set<Rule> matches = new LinkedHashSet<>();
        boolean rain = codes.stream().anyMatch(c -> c >= 200 && c < 600);
        boolean recognized = false;
        for (int code : codes) {
            if (code >= 200 && code <= 232 || Set.of(302, 312, 314, 502, 503, 504, 522).contains(code)) {
                matches.add(byId.get("heavy_rain"));
                recognized = true;
            } else if (Set.of(300, 301, 310, 311, 313, 321, 500, 520).contains(code)) {
                matches.add(byId.get("light_rain"));
                recognized = true;
            } else if (Set.of(501, 521, 531).contains(code)) {
                matches.add(byId.get("moderate_rain"));
                recognized = true;
            } else if (Set.of(701, 721, 741).contains(code)) {
                matches.add(byId.get(visibilityMeters < 5000 ? "fog" : "mist"));
                recognized = true;
            } else if (code >= 800 && code <= 804) {
                recognized = true;
                if (code >= 803) matches.add(byId.get("cloudy"));
                if (feelsLike >= 32 && feelsLike <= 40) {
                    if (code == 800 && !rain && windKmh <= 30) matches.add(byId.get("clear_warm"));
                    if (code == 801 || code == 802) matches.add(byId.get("partly_warm"));
                }
                if (code == 800 && feelsLike >= 26 && feelsLike < 32) matches.add(byId.get("clear_mild"));
            }
        }
        if (windKmh > 30) matches.add(byId.get("wind"));
        if (recognized) {
            if (feelsLike > 40) matches.add(byId.get("extreme_heat"));
            if (feelsLike < 26) matches.add(byId.get("cool"));
            if (visibilityValid && visibilityMeters < 5000) matches.add(byId.get("fog"));
        }
        if (matches.isEmpty()) {
            return new Estimate(null, null, "Información insuficiente",
                    "Con estas condiciones no es posible establecer un porcentaje de ocupación con suficiente respaldo. Se necesitan más datos para caracterizar la afluencia en esta situación.",
                    "Faltan datos para estimar la afluencia con estas condiciones.");
        }
        Rule selected = matches.stream().min(Comparator.comparing(Rule::minimum).thenComparing(Rule::maximum)).orElseThrow();
        String reasons = matches.stream().map(r -> effect(r.id(), feelsLike, windKmh, visibilityMeters)).collect(Collectors.joining(" "));
        return new Estimate(selected.minimum(), selected.maximum(), selected.condition(),
                "La ocupación de la playa podría situarse entre el " + selected.minimum() + " % y el " + selected.maximum()
                + " %. " + reasons + " La estimación considera en conjunto estas condiciones y el factor que más limita la afluencia.",
                effect(selected.id(), feelsLike, windKmh, visibilityMeters));
    }

    private String effect(String id, double feelsLike, double windKmh, Double visibilityMeters) {
        return switch (id) {
            case "clear_warm" -> "El cielo despejado y el ambiente cálido favorecen la visita y permanencia en la playa.";
            case "partly_warm" -> "El ambiente cálido con nubes parciales favorece una afluencia moderada a alta.";
            case "clear_mild" -> "El cielo despejado y el ambiente templado permiten esperar una afluencia moderada.";
            case "extreme_heat" -> "La sensación térmica de " + number(feelsLike) + " °C indica calor extremo, que puede reducir la permanencia al aire libre.";
            case "cloudy" -> "La nubosidad predominante puede disminuir el interés por permanecer en la playa.";
            case "cool" -> "La sensación térmica de " + number(feelsLike) + " °C corresponde a un ambiente fresco que puede limitar la afluencia.";
            case "mist" -> "Hay bruma o neblina, aunque la visibilidad de " + number(visibilityMeters / 1000) + " km permite esperar cierta afluencia.";
            case "fog" -> "La visibilidad reducida a " + number(visibilityMeters / 1000) + " km puede desalentar la visita y acortar la permanencia.";
            case "light_rain" -> "La llovizna o lluvia ligera puede reducir la afluencia y el tiempo de permanencia; no se conoce cuánto durará.";
            case "moderate_rain" -> "La lluvia moderada puede reducir considerablemente la afluencia y la permanencia en la playa.";
            case "heavy_rain" -> "La lluvia intensa o tormenta hace prever una afluencia muy reducida.";
            case "wind" -> "El viento de " + number(windKmh) + " km/h puede dificultar la comodidad y reducir la permanencia en la playa.";
            default -> "";
        };
    }

    private static String number(double value) {
        return String.format(Locale.forLanguageTag("es-CO"), "%.1f", value);
    }
    private Estimate unavailable(String id, String reason) {
        return new Estimate(null, null, byId.get(id).condition(), reason, reason);
    }
}
