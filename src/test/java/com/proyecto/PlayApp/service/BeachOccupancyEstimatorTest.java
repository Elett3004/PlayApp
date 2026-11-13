package com.proyecto.PlayApp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class BeachOccupancyEstimatorTest {
    private final BeachOccupancyEstimator estimator = new BeachOccupancyEstimator();

    @ParameterizedTest
    @CsvSource({
        "800,32,10,10000,60,80", "800,40,10,10000,60,80",
        "800,31.99,10,10000,40,60", "800,26,10,10000,40,60",
        "800,25.99,10,10000,20,40", "800,40.01,10,10000,60,75",
        "801,35,10,10000,50,70", "802,35,10,10000,50,70",
        "803,35,10,10000,20,40", "804,41,30,10000,20,40",
        "804,41,30.01,10000,15,35", "800,35,30.01,10000,15,35",
        "701,35,10,5000,30,50", "721,35,10,10000,30,50",
        "741,35,10,4999,10,25", "800,35,10,4999,10,25",
        "300,35,10,10000,15,35", "301,35,10,10000,15,35",
        "500,35,10,10000,15,35", "520,35,10,10000,15,35",
        "501,35,10,10000,5,20", "521,35,10,10000,5,20",
        "531,35,10,10000,5,20", "502,35,10,10000,0,10",
        "503,35,10,10000,0,10", "504,35,10,10000,0,10",
        "522,35,10,10000,0,10", "211,35,10,10000,0,10",
        "200,35,40,10000,0,10", "302,35,10,10000,0,10"
    })
    void appliesApprovedPercentageRanges(int code, double feelsLike, double wind, double visibility, int min, int max) {
        var result = estimator.estimate(List.of(code), feelsLike, wind, visibility, true);
        assertThat(result.minimum()).isEqualTo(min);
        assertThat(result.maximum()).isEqualTo(max);
    }

    @Test void evaluatesAllConditionsAndChoosesLowestRangeWithoutAveraging() {
        var result = estimator.estimate(List.of(800, 501, 721), 41, 40, 10000.0, true);
        assertThat(result.minimum()).isEqualTo(5);
        assertThat(result.maximum()).isEqualTo(20);
        assertThat(result.explanation()).contains("viento de", "calor extremo", "bruma", "lluvia moderada", "5 % y el 20 %")
                .doesNotContain("Regla aplicada", "Origen:", "tabla", "menor mínimo");
        var reversed = estimator.estimate(List.of(721, 501, 800), 41, 40, 10000.0, true);
        assertThat(reversed.minimum()).isEqualTo(result.minimum());
        assertThat(reversed.maximum()).isEqualTo(result.maximum());
    }

    @Test void preservesUnknownAndMissingDataInsteadOfClaimingOptimalOrZeroOccupancy() {
        assertThat(estimator.estimate(List.of(721), 35, 10, null, true).minimum()).isNull();
        assertThat(estimator.estimate(List.of(741), 35, 10, -1.0, true).minimum()).isNull();
        var night = estimator.estimate(List.of(804), 35, 40, 10000.0, false);
        assertThat(night.minimum()).isEqualTo(0);
        assertThat(night.maximum()).isEqualTo(1);
        assertThat(night.summary()).contains("puede haber algunas personas");
        assertThat(estimator.estimate(List.of(721), 35, 10, null, false).maximum()).isEqualTo(1);
        assertThat(estimator.estimate(List.of(511), 35, 10, 10000.0, true).minimum()).isNull();
        assertThat(estimator.estimate(List.of(711), 35, 10, 10000.0, true).minimum()).isNull();
        assertThat(estimator.estimate(List.of(), 35, 10, 10000.0, true).minimum()).isNull();
        assertThat(estimator.estimate(List.of(800), Double.NaN, 10, 10000.0, true).minimum()).isNull();
        assertThat(estimator.estimate(List.of(800), 35, 10, 10000.0, true).maximum()).isEqualTo(80);
    }
}
