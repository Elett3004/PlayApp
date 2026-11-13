package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CurrentWeatherService {
    public record Metric(String label, String value) {
        public String shortLabel() {
            return switch (label) {
                case "Presión atmosférica" -> "Presión";
                case "Lluvia (última hora)" -> "Lluvia · 1 h";
                default -> label;
            };
        }
        public String icon() {
            return switch (label) {
                case "Viento" -> "wind";
                case "Humedad" -> "drop";
                case "Visibilidad" -> "eye";
                case "Nubosidad" -> "cloud";
                case "Presión atmosférica" -> "gauge";
                default -> "rain";
            };
        }
    }
    public record Location(double latitude, double longitude) {}
    public record Weather(String description, String observedAt, String consultedAt,
                          List<Metric> metrics, BeachOccupancyEstimator.Estimate estimate, WeatherArtwork.Artwork artwork,
                          NeuralOccupancyClient.Prediction prediction) {
        public Weather(String description, String observedAt, String consultedAt, List<Metric> metrics,
                       BeachOccupancyEstimator.Estimate estimate, WeatherArtwork.Artwork artwork) {
            this(description, observedAt, consultedAt, metrics, estimate, artwork, null);
        }
        public String predictionLabel() { return prediction == null ? "Reglas · respaldo" : "Red neuronal · simulación"; }
        public String occupancySummary() { return prediction == null ? estimate.summary() : "Estimación de la red neuronal según el clima actual, la hora y el calendario turístico. Entrenada con datos simulados."; }
        public String analysis() { return prediction == null ? estimate.explanation() : "La red neuronal estima una ocupación del " + String.format(Locale.forLanguageTag("es-CO"), "%.1f", prediction.percentage()) + " %. Considera clima, hora local, día de semana, festivos y temporadas configuradas. Versión: " + prediction.version() + ". Entrenada con datos simulados; pendiente de validación con observaciones reales del grupo."; }
        public String temperature() { return value("Temperatura"); }
        public String feelsLike() { return value("Sensación térmica"); }
        public String observationTime() { return observedAt.length() >= 16 ? observedAt.substring(11, 16) : observedAt; }
        public String sunrise() { return time(value("Amanecer")); }
        public String sunset() { return time(value("Atardecer")); }
        public List<Metric> highlights() {
            return metrics.stream().filter(m -> List.of("Viento", "Humedad", "Visibilidad", "Nubosidad", "Presión atmosférica", "Lluvia (última hora)").contains(m.label())).toList();
        }
        public List<Metric> additionalMetrics() {
            return metrics.stream().filter(m -> !highlights().contains(m) && !List.of("Temperatura", "Sensación térmica", "Amanecer", "Atardecer").contains(m.label())).toList();
        }
        private String value(String label) {
            return metrics.stream().filter(m -> m.label().equals(label)).map(Metric::value).findFirst().orElse("No informado");
        }
        private String time(String value) { return value.length() == 19 ? value.substring(11, 16) : value; }
    }

    public Location location() { return new Location(latitude, longitude); }

    private final RestClient client;
    private final String apiKey;
    private final double latitude;
    private final double longitude;
    private final BeachOccupancyEstimator estimator;
    private NeuralOccupancyClient neuralModel;
    private Weather cached;
    private Instant expiresAt = Instant.MIN;
    private Instant retryAfter = Instant.MIN;
    private String lastError;
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZONE);

    @org.springframework.beans.factory.annotation.Autowired
    public CurrentWeatherService(RestClient.Builder builder,
            @Value("${playapp.weather.api-key:}") String apiKey,
            @Value("${playapp.weather.latitude:10.399667}") double latitude,
            @Value("${playapp.weather.longitude:-75.555001}") double longitude,
            BeachOccupancyEstimator estimator, NeuralOccupancyClient neuralModel) {
        this(buildClient(builder), apiKey, latitude, longitude, estimator);
        this.neuralModel = neuralModel;
    }

    CurrentWeatherService(RestClient client, String apiKey, double latitude, double longitude,
                          BeachOccupancyEstimator estimator) {
        this.client = client;
        this.apiKey = apiKey;
        this.latitude = latitude;
        this.longitude = longitude;
        this.estimator = estimator;
    }

    private static RestClient buildClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return builder.baseUrl("https://api.openweathermap.org").requestFactory(factory).build();
    }

    public synchronized Weather current() {
        if (apiKey.isBlank()) throw new IllegalStateException("Falta configurar OPENWEATHER_API_KEY en el servidor.");
        Instant now = Instant.now();
        if (cached != null && now.isBefore(expiresAt)) return cached;
        if (now.isBefore(retryAfter)) throw new IllegalStateException(lastError);
        try {
            JsonNode data = client.get().uri(uri -> uri.path("/data/2.5/weather")
                    .queryParam("lat", latitude).queryParam("lon", longitude)
                    .queryParam("units", "metric").queryParam("lang", "es")
                    .queryParam("appid", apiKey).build()).retrieve().body(JsonNode.class);
            Weather weather = parse(data, now);
            cached = weather;
            expiresAt = now.plusSeconds(600);
            return weather;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String message = switch (status) {
                case 401, 403 -> "OpenWeatherMap rechazó la clave o el acceso. Verifica que esté activa para clima actual.";
                case 429 -> "Se alcanzó el límite de consultas de OpenWeatherMap. Intenta más tarde.";
                default -> "OpenWeatherMap no está disponible. Intenta nuevamente en unos minutos.";
            };
            throw failure(message, now, status == 429 ? 600 : 60);
        } catch (RuntimeException ex) {
            // Never expose HTTP exception messages: they can contain the API key in the URI.
            throw failure("No fue posible obtener datos meteorológicos válidos. Intenta nuevamente en unos minutos.", now, 60);
        }
    }

    private IllegalStateException failure(String message, Instant now, int seconds) {
        lastError = message;
        retryAfter = now.plusSeconds(seconds);
        return new IllegalStateException(message);
    }

    private Weather parse(JsonNode data, Instant consulted) {
        if (data == null) throw new IllegalArgumentException();
        double temperature = required(data.path("main"), "temp");
        double feelsLike = required(data.path("main"), "feels_like");
        double wind = required(data.path("wind"), "speed") * 3.6;
        List<Integer> codes = new ArrayList<>();
        if (!data.path("weather").isArray() || data.path("weather").isEmpty()) throw new IllegalArgumentException();
        for (JsonNode condition : data.path("weather")) codes.add((int) required(condition, "id"));
        long observed = (long) required(data, "dt");
        long sunrise = (long) required(data.path("sys"), "sunrise");
        long sunset = (long) required(data.path("sys"), "sunset");
        if (observed < consulted.minusSeconds(3600).getEpochSecond() || observed > consulted.plusSeconds(300).getEpochSecond()) {
            throw new IllegalArgumentException("Stale observation");
        }
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("Temperatura", decimal(temperature, "°C")));
        metrics.add(new Metric("Sensación térmica", decimal(feelsLike, "°C")));
        add(metrics, "Mínima actual en el área", data.path("main"), "temp_min", 1, "°C");
        add(metrics, "Máxima actual en el área", data.path("main"), "temp_max", 1, "°C");
        metrics.add(new Metric("Viento", decimal(wind, "km/h")));
        add(metrics, "Ráfagas", data.path("wind"), "gust", 3.6, "km/h");
        add(metrics, "Dirección del viento", data.path("wind"), "deg", 1, "°");
        add(metrics, "Humedad", data.path("main"), "humidity", 1, "%");
        add(metrics, "Nubosidad", data.path("clouds"), "all", 1, "%");
        add(metrics, "Presión atmosférica", data.path("main"), "pressure", 1, "hPa");
        add(metrics, "Presión al nivel del mar", data.path("main"), "sea_level", 1, "hPa");
        add(metrics, "Presión a nivel del suelo", data.path("main"), "grnd_level", 1, "hPa");
        add(metrics, "Visibilidad", data, "visibility", .001, "km");
        add(metrics, "Lluvia (última hora)", data.path("rain"), "1h", 1, "mm");
        metrics.add(new Metric("Amanecer", FORMAT.format(Instant.ofEpochSecond(sunrise))));
        metrics.add(new Metric("Atardecer", FORMAT.format(Instant.ofEpochSecond(sunset))));
        String description = data.path("weather").path(0).path("description").asText("Sin descripción");
        Double visibility = data.path("visibility").isNumber() ? data.path("visibility").asDouble() : null;
        boolean daylight = consulted.getEpochSecond() >= sunrise && consulted.getEpochSecond() < sunset;
        var estimate = estimator.estimate(codes, feelsLike, wind, visibility, daylight);
        String context = "En Bocagrande se registran " + decimal(temperature, "°C") + ", con " + description
                + ", una sensación térmica de " + decimal(feelsLike, "°C") + " y viento de " + decimal(wind, "km/h") + ". ";
        if (data.path("main").path("humidity").isNumber()) {
            context += "La humedad es del " + decimal(data.path("main").path("humidity").asDouble(), "%") + ". ";
        }
        if (data.path("clouds").path("all").isNumber()) {
            context += "La nubosidad cubre el " + decimal(data.path("clouds").path("all").asDouble(), "%") + " del cielo. ";
        }
        if (data.path("rain").path("1h").isNumber()) {
            context += "La precipitación reportada para la última hora es de " + decimal(data.path("rain").path("1h").asDouble(), "mm") + ". ";
        }
        var summary = new BeachOccupancyEstimator.Estimate(estimate.minimum(), estimate.maximum(), estimate.condition(), context + estimate.explanation(), estimate.summary());
        return new Weather(description,
                FORMAT.format(Instant.ofEpochSecond(observed)), FORMAT.format(consulted), List.copyOf(metrics),
                summary, WeatherArtwork.select(codes, feelsLike, wind, visibility, daylight),
                neuralModel == null ? null : neuralModel.predict(data, codes, daylight).orElse(null));
    }

    private static double required(JsonNode node, String name) {
        if (!node.path(name).isNumber() || !Double.isFinite(node.path(name).asDouble())) throw new IllegalArgumentException();
        return node.path(name).asDouble();
    }

    private static void add(List<Metric> metrics, String label, JsonNode node, String field, double multiplier, String unit) {
        metrics.add(new Metric(label, node.path(field).isNumber() ? decimal(node.path(field).asDouble() * multiplier, unit) : "No informado"));
    }

    private static String decimal(double value, String unit) {
        return String.format(Locale.forLanguageTag("es-CO"), "%.1f %s", value, unit);
    }
}
