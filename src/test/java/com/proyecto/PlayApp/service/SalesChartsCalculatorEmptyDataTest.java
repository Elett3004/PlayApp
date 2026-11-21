package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.service.SalesChartsCalculator.Range;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class SalesChartsCalculatorEmptyDataTest {
    @Test
    void nullOrEmptyMongoResultsProduceACompleteZeroedChartResponse() {
        LocalDate today = LocalDate.of(2026, 11, 21);
        Range range = SalesChartsCalculator.resolveRange("30d", null, null, "auto", today, null);

        Map<String, Object> response = SalesChartsCalculator.compute(range, null, null, null);

        assertThat(response).containsKeys(
                "filters", "categories", "kpis", "trend", "products", "heatmap", "categoryMix", "resumen");
        Map<String, Object> kpis = (Map<String, Object>) response.get("kpis");
        assertThat(((Map<String, Object>) kpis.get("ingresos")).get("actual")).isEqualTo(0.0);
        assertThat(((Map<String, Object>) kpis.get("pedidos")).get("actual")).isEqualTo(0.0);
        assertThat(((Map<String, Object>) kpis.get("ticketPromedio")).get("actual")).isEqualTo(0.0);
        assertThat(((Map<String, Object>) kpis.get("unidades")).get("actual")).isEqualTo(0.0);

        Map<String, Object> trend = (Map<String, Object>) response.get("trend");
        assertThat((List<?>) trend.get("labels")).hasSize(30);
        assertThat((List<Double>) trend.get("ingresos")).hasSize(30).containsOnly(0.0);
        assertThat((List<?>) response.get("products")).isEmpty();
        assertThat((List<?>) response.get("categories")).hasSize(3);
        assertThat((List<?>) response.get("categoryMix")).hasSize(3);

        Map<String, Object> heatmap = (Map<String, Object>) response.get("heatmap");
        assertThat((List<List<Double>>) heatmap.get("pedidos")).hasSize(7)
                .allSatisfy(row -> assertThat(row).hasSize(24).containsOnly(0.0));
    }
}
