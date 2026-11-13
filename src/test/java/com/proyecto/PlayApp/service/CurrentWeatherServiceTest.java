package com.proyecto.PlayApp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CurrentWeatherServiceTest {
    private MockRestServiceServer server;
    private CurrentWeatherService service;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.openweathermap.org");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new CurrentWeatherService(builder.build(), "test-secret", 10.399667, -75.555001, new BeachOccupancyEstimator());
    }

    private String weather(long dt) {
        return """
            {"dt":%d,"main":{"temp":30,"feels_like":35,"humidity":75},
             "weather":[{"id":804,"description":"cielo cubierto"}],"wind":{"speed":10},
             "sys":{"sunrise":%d,"sunset":%d}}
            """.formatted(dt, dt-100, dt+100);
    }

    @Test void convertsWindUsesCoordinatesAndCachesResponse() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/weather?lat=10.399667&lon=-75.555001")))
                .andExpect(method(HttpMethod.GET)).andExpect(queryParam("units", "metric"))
                .andExpect(queryParam("lang", "es")).andRespond(withSuccess(weather(Instant.now().getEpochSecond()), MediaType.APPLICATION_JSON));
        var result = service.current();
        assertThat(result.estimate().minimum()).isEqualTo(15);
        assertThat(result.metrics()).anyMatch(m -> m.label().equals("Viento") && m.value().startsWith("36"));
        assertThat(result.metrics()).anyMatch(m -> m.label().equals("Lluvia (última hora)") && m.value().equals("No informado"));
        assertThat(service.current()).isSameAs(result);
        server.verify();
    }

    @Test void hidesApiKeyAndBacksOffAfterUnauthorized() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("test-secret"));
        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class).hasMessageContaining("rechazó").hasMessageNotContaining("test-secret");
        assertThatThrownBy(service::current).hasMessageNotContaining("test-secret");
        server.verify();
    }

    @Test void rejectsStaleObservations() {
        server.expect(anything()).andRespond(withSuccess(weather(Instant.now().minusSeconds(7200).getEpochSecond()), MediaType.APPLICATION_JSON));
        assertThatThrownBy(service::current).hasMessageContaining("válidos");
    }

    @Test void passesVisibilityAndSecondaryConditionsToEstimator() {
        String body = weather(Instant.now().getEpochSecond())
                .replace("\"id\":804", "\"id\":721")
                .replace("\"wind\":{\"speed\":10}", "\"wind\":{\"speed\":2},\"visibility\":6000");
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = service.current();
        assertThat(result.estimate().minimum()).isEqualTo(30);
        assertThat(result.estimate().maximum()).isEqualTo(50);
        assertThat(result.artwork().key()).isEqualTo("mist");
        assertThat(result.estimate().explanation()).contains("En Bocagrande", "30,0 °C", "35,0 °C", "7,2 km/h", "75,0 %", "6,0 km", "30 % y el 50 %")
                .doesNotContain("Regla aplicada", "Origen:", "tabla");
        server.verify();
    }

    @Test void evaluatesSecondaryStormDespitePrimaryClearSky() {
        String body = weather(Instant.now().getEpochSecond())
                .replace("\"id\":804", "\"id\":800")
                .replace("\"description\":\"cielo cubierto\"}", "\"description\":\"despejado\"},{\"id\":211}");
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = service.current();
        assertThat(result.estimate().maximum()).isEqualTo(10);
        assertThat(result.artwork().key()).isEqualTo("thunderstorm");
        server.verify();
    }
}
