package com.proyecto.PlayApp.service;

import java.util.List;
import java.util.Set;

/** Decorative photos selected from weather codes, never from translated descriptions. */
public final class WeatherArtwork {
    private WeatherArtwork() {}

    public record Artwork(String key, boolean night) {
        public String imageUrl() { return "/img/weather/" + key + ".jpg"; }
    }

    public static Artwork select(List<Integer> codes, double feelsLike, double windKmh, Double visibilityMeters, boolean daylight) {
        String key = selectKey(codes, feelsLike, windKmh, visibilityMeters, daylight);
        return new Artwork(key, !daylight);
    }

    private static String selectKey(List<Integer> codes, double feelsLike, double windKmh, Double visibilityMeters, boolean daylight) {
        if (codes == null || codes.isEmpty()) return "shore-background";
        // Preserve adverse conditions even if a clearer condition appears first.
        if (codes.contains(781)) return "tornado";
        if (codes.stream().anyMatch(c -> c >= 200 && c < 300)) return "thunderstorm";
        if (codes.contains(511) || codes.stream().anyMatch(c -> c >= 600 && c < 700)) return "snow";
        if (codes.stream().anyMatch(c -> Set.of(302, 312, 314, 502, 503, 504, 522).contains(c))) return "heavy-rain";
        if (codes.stream().anyMatch(c -> Set.of(501, 521, 531).contains(c))) return "moderate-rain";
        if (codes.stream().anyMatch(c -> c >= 300 && c < 400 || c == 500 || c == 520)) return "light-rain";
        if (codes.stream().anyMatch(c -> Set.of(711, 731, 751, 761, 762).contains(c))) return "dust";
        if (codes.stream().anyMatch(c -> Set.of(701, 721, 741).contains(c))) {
            boolean lowVisibility = visibilityMeters != null && Double.isFinite(visibilityMeters) && visibilityMeters >= 0 && visibilityMeters < 5000;
            return lowVisibility || codes.contains(741) ? "fog" : "mist";
        }
        if (codes.contains(771) || windKmh > 30) return "wind";
        boolean clouds = codes.stream().anyMatch(c -> c >= 801 && c <= 804);
        boolean clear = codes.contains(800);
        if (!clear && !clouds) return "shore-background";
        if (!daylight && clear && !clouds) return "night";
        if (codes.contains(803) || codes.contains(804)) return "cloudy";
        if (codes.contains(801) || codes.contains(802)) return "partly-cloudy";
        if (feelsLike > 40) return "heat";
        if (feelsLike < 26) return "cool";
        return "clear";
    }
}
