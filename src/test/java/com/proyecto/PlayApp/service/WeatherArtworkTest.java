package com.proyecto.PlayApp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WeatherArtworkTest {
    @ParameterizedTest
    @CsvSource({
        "200,thunderstorm", "201,thunderstorm", "202,thunderstorm", "210,thunderstorm", "211,thunderstorm", "212,thunderstorm", "221,thunderstorm", "230,thunderstorm", "231,thunderstorm", "232,thunderstorm",
        "300,light-rain", "301,light-rain", "302,heavy-rain", "310,light-rain", "311,light-rain", "312,heavy-rain", "313,light-rain", "314,heavy-rain", "321,light-rain",
        "500,light-rain", "501,moderate-rain", "502,heavy-rain", "503,heavy-rain", "504,heavy-rain", "511,snow", "520,light-rain", "521,moderate-rain", "522,heavy-rain", "531,moderate-rain",
        "600,snow", "601,snow", "602,snow", "611,snow", "612,snow", "613,snow", "615,snow", "616,snow", "620,snow", "621,snow", "622,snow",
        "701,mist", "711,dust", "721,mist", "731,dust", "741,fog", "751,dust", "761,dust", "762,dust", "771,wind", "781,tornado",
        "800,clear", "801,partly-cloudy", "802,partly-cloudy", "803,cloudy", "804,cloudy", "999,shore-background"
    })
    void mapsWeatherCodesToPackagedImages(int code, String expected) throws Exception {
        var artwork = WeatherArtwork.select(List.of(code), 35, 10, 10000.0, true);
        assertThat(artwork.key()).isEqualTo(expected);
        try (var input = getClass().getResourceAsStream("/static" + artwork.imageUrl())) {
            assertThat(input).as(artwork.imageUrl()).isNotNull();
            assertThat(javax.imageio.ImageIO.read(input)).isNotNull();
        }
    }

    @Test void accountsForHeatWindVisibilityNightAndConcurrentWeather() {
        assertThat(WeatherArtwork.select(List.of(800), 40.01, 10, 10000.0, true).key()).isEqualTo("heat");
        assertThat(WeatherArtwork.select(List.of(800), 25.99, 10, 10000.0, true).key()).isEqualTo("cool");
        assertThat(WeatherArtwork.select(List.of(800), 40, 30, 10000.0, true).key()).isEqualTo("clear");
        assertThat(WeatherArtwork.select(List.of(800), 35, 30.01, 10000.0, true).key()).isEqualTo("wind");
        assertThat(WeatherArtwork.select(List.of(721), 35, 10, 4999.0, true).key()).isEqualTo("fog");
        assertThat(WeatherArtwork.select(List.of(721), 35, 10, 5000.0, true).key()).isEqualTo("mist");
        assertThat(WeatherArtwork.select(List.of(800), 35, 10, 10000.0, false).key()).isEqualTo("night");
        var storm = WeatherArtwork.select(List.of(800, 211), 42, 40, 10000.0, false);
        assertThat(storm.key()).isEqualTo("thunderstorm");
        assertThat(storm.night()).isTrue();
        assertThat(WeatherArtwork.select(List.of(800, 500), 42, 40, 10000.0, true).key()).isEqualTo("light-rain");
        assertThat(WeatherArtwork.select(List.of(), 35, 10, null, true).key()).isEqualTo("shore-background");
    }
}
