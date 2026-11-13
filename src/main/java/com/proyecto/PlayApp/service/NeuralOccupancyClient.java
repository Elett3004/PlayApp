package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Service
public class NeuralOccupancyClient {
    public record Prediction(double percentage, String version) {}
    private final RestClient client;

    @Autowired
    public NeuralOccupancyClient(RestClient.Builder builder, @Value("${playapp.occupancy.model-url:http://localhost:8001}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000); factory.setReadTimeout(3000);
        client = builder.clone().baseUrl(url).requestFactory(factory).build();
    }
    NeuralOccupancyClient(RestClient client) { this.client = client; }

    public Optional<Prediction> predict(JsonNode data, List<Integer> codes, boolean daylight) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("observed_at", Instant.ofEpochSecond(data.path("dt").asLong()).toString());
        body.put("weather_codes", codes); body.put("daylight", daylight);
        put(body,"temperature_c",data.path("main"),"temp",1);
        put(body,"feels_like_c",data.path("main"),"feels_like",1);
        put(body,"humidity_pct",data.path("main"),"humidity",1);
        put(body,"wind_kmh",data.path("wind"),"speed",3.6);
        put(body,"gust_kmh",data.path("wind"),"gust",3.6);
        put(body,"clouds_pct",data.path("clouds"),"all",1);
        put(body,"visibility_m",data,"visibility",1);
        put(body,"rain_1h_mm",data.path("rain"),"1h",1);
        put(body,"pressure_hpa",data.path("main"),"pressure",1);
        try {
            JsonNode result = client.post().uri("/predict").body(body).retrieve().body(JsonNode.class);
            if (result == null || !result.path("occupancy_pct").isNumber()
                    || !"neural_network".equals(result.path("source").asText())
                    || !"SIMULATED_ONLY".equals(result.path("training_data").asText())
                    || result.path("model_version").asText().isBlank()) return Optional.empty();
            double pct = result.path("occupancy_pct").asDouble();
            if (!Double.isFinite(pct) || pct < 0 || pct > (daylight ? 100 : 1)) return Optional.empty();
            return Optional.of(new Prediction(pct, result.path("model_version").asText()));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }
    private static void put(Map<String,Object> body,String key,JsonNode node,String field,double multiplier) {
        body.put(key,node.path(field).isNumber() ? node.path(field).asDouble()*multiplier : null);
    }
}
