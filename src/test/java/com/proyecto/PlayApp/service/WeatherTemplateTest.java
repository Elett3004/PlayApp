package com.proyecto.PlayApp.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WeatherTemplateTest {
    private String render(CurrentWeatherService.Weather weather, String error) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet);
        var response = new MockHttpServletResponse();
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, response);
        var context = new WebContext(exchange);
        context.setVariable("weather", weather);
        context.setVariable("weatherError", error);
        context.setVariable("weatherLocation", new CurrentWeatherService.Location(10.399667, -75.555001));
        context.setVariable("adminGlobal", false);
        context.setVariable("nombreAdmin", "Administrador");
        return engine.process("Management/clima", context);
    }

    @Test void rendersRangesAndUnmatchedConditionsAndErrors() {
        var estimator = new BeachOccupancyEstimator();
        var weather = new CurrentWeatherService.Weather("cielo cubierto", "13/11/2026 10:00", "13/11/2026 10:05",
                List.of(new CurrentWeatherService.Metric("Temperatura", "30 °C"),
                        new CurrentWeatherService.Metric("Sensación térmica", "35 °C"),
                        new CurrentWeatherService.Metric("Viento", "10 km/h"),
                        new CurrentWeatherService.Metric("Humedad", "74 %"),
                        new CurrentWeatherService.Metric("Visibilidad", "10 km"),
                        new CurrentWeatherService.Metric("Nubosidad", "80 %"),
                        new CurrentWeatherService.Metric("Presión atmosférica", "1010 hPa"),
                        new CurrentWeatherService.Metric("Ráfagas", "14 km/h"),
                        new CurrentWeatherService.Metric("Lluvia (última hora)", "No informado"),
                        new CurrentWeatherService.Metric("Amanecer", "13/11/2026 05:58:00"),
                        new CurrentWeatherService.Metric("Atardecer", "13/11/2026 17:34:00")), estimator.estimate(List.of(804), 35, 10, 10000.0, true), WeatherArtwork.select(List.of(804), 35, 10, 10000.0, true));
        String html = render(weather, null);
        var neuralWeather = new CurrentWeatherService.Weather(weather.description(), weather.observedAt(), weather.consultedAt(),
                weather.metrics(), weather.estimate(), weather.artwork(), new NeuralOccupancyClient.Prediction(67.3, "sim-v1-test"));
        assertThat(render(neuralWeather, null)).contains("neural-occupancy", "Red neuronal · simulación", "sim-v1-test")
                .doesNotContain("Reglas · respaldo", "class=\"occupancy-band\"");
        assertThat(html).contains("--weather-photo:url(/img/weather/cloudy.jpg)");
        assertThat(html).contains("20</span>–<span>40", "30 °C", "logoutModal", "La ocupación de la playa podría situarse", "nubosidad predominante", "10 km/h", "74 %", "id=\"weather-map\"", "data-latitude=\"10.399667\"", "data-longitude=\"-75.555001\"", "05:58", "17:34")
                .doesNotContain("Reglas de referencia", "Regla aplicada", "Origen:", "Tabla_porcentajes", "<table", "menor mínimo");
        assertThat(html).contains("data-bs-target=\"#weather-analysis-modal\"", "id=\"weather-analysis-modal\"",
                "aria-labelledby=\"weather-analysis-title\"", "modal-dialog-scrollable", "data-bs-dismiss=\"modal\"",
                "Ver análisis y más datos", "Ráfagas", "14 km/h")
                .doesNotContain("<details");
        var unknown = new CurrentWeatherService.Weather("despejado", "hoy", "hoy", List.of(), estimator.estimate(List.of(721), 35, 10, null, true), WeatherArtwork.select(List.of(721), 35, 10, null, true));
        assertThat(render(unknown, null)).containsPattern("Sin estimación\\s+concluyente");
        assertThat(render(null, "Servicio temporalmente no disponible")).contains("Servicio temporalmente no disponible")
                .doesNotContain("class=\"occupancy-value\"", "id=\"weather-analysis-modal\"");
    }
}
