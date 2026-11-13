package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NeuralOccupancyClientTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="occupancy.live", matches="true")
    void javaCanCallTheRunningTensorflowService() throws Exception {
        var client=new NeuralOccupancyClient(RestClient.builder().baseUrl("http://127.0.0.1:8001").build());
        var input=mapper.readTree("{\"dt\":1794571200,\"main\":{\"temp\":32,\"feels_like\":38,\"humidity\":75},\"wind\":{\"speed\":3},\"clouds\":{\"all\":20},\"visibility\":10000}");
        var prediction=client.predict(input,List.of(801),true).orElseThrow();
        assertThat(prediction.percentage()).isBetween(0.0,100.0);
        assertThat(prediction.version()).startsWith("sim-v1-");
    }
    @Test void passesRawWeatherUnitsAndAcceptsOnlyFiniteVersionedPredictions() throws Exception {
        var builder=RestClient.builder().baseUrl("http://localhost:8001");
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new NeuralOccupancyClient(builder.build());
        server.expect(requestTo("http://localhost:8001/predict"))
                .andExpect(jsonPath("$.wind_kmh").value(36.0))
                .andExpect(jsonPath("$.rain_1h_mm").isEmpty())
                .andExpect(jsonPath("$.observed_at").value("2026-11-13T12:00:00Z"))
                .andRespond(withSuccess("{\"occupancy_pct\":67.3,\"source\":\"neural_network\",\"training_data\":\"SIMULATED_ONLY\",\"model_version\":\"sim-v1-test\"}",MediaType.APPLICATION_JSON));
        var input=mapper.readTree("{\"dt\":1794571200,\"main\":{\"temp\":32,\"feels_like\":38},\"wind\":{\"speed\":10}}");
        assertThat(client.predict(input,List.of(800),true).orElseThrow().percentage()).isEqualTo(67.3);
        server.verify();
    }
    @Test void providerFailureOrInvalidPercentageTriggersFallback() throws Exception {
        var builder=RestClient.builder().baseUrl("http://localhost:8001");
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new NeuralOccupancyClient(builder.build());
        server.expect(anything()).andRespond(withServerError());
        server.expect(anything()).andRespond(withSuccess("{\"occupancy_pct\":101,\"source\":\"neural_network\",\"training_data\":\"SIMULATED_ONLY\",\"model_version\":\"test\"}",MediaType.APPLICATION_JSON));
        var input=mapper.readTree("{}");
        assertThat(client.predict(input,List.of(800),true)).isEmpty();
        assertThat(client.predict(input,List.of(800),true)).isEmpty();
        server.verify();
    }
}
