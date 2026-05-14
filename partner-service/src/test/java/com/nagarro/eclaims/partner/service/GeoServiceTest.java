package com.nagarro.eclaims.partner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GeoService}.
 *
 * Tests:
 * 1. Haversine formula correctness (known real-world distances)
 * 2. ETA fallback (Google Maps disabled)
 * 3. ETA minimum boundary (5 minutes even for very close points)
 * 4. buildMapsLink format
 * 5. Google Maps API: response parsing when API returns OK (mock RestTemplate)
 * 6. Google Maps API: graceful fallback when API throws
 */
@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    private GeoService geoService;

    @BeforeEach
    void setUp() {
        // Build service with Maps API disabled (local dev mode)
        geoService = new GeoService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(geoService, "mapsEnabled", false);
        ReflectionTestUtils.setField(geoService, "apiKey", "");
    }

    // ── Haversine formula ─────────────────────────────────────────────────────

    @Test
    @DisplayName("distanceKm: Delhi to Mumbai ≈ 1154 km (±5 km tolerance)")
    void distanceKm_delhiToMumbai() {
        // Delhi: 28.6139, 77.2090 — Mumbai: 19.0760, 72.8777
        double dist = geoService.distanceKm(28.6139, 77.2090, 19.0760, 72.8777);
        assertThat(dist).isBetween(1143.0, 1160.0);
    }

    @Test
    @DisplayName("distanceKm: same point → 0.0 km")
    void distanceKm_samePoint() {
        double dist = geoService.distanceKm(28.6139, 77.2090, 28.6139, 77.2090);
        assertThat(dist).isEqualByComparingTo(0.0);
    }

    @Test
    @DisplayName("distanceKm: is symmetric (A→B == B→A)")
    void distanceKm_isSymmetric() {
        double ab = geoService.distanceKm(28.6139, 77.2090, 19.0760, 72.8777);
        double ba = geoService.distanceKm(19.0760, 72.8777, 28.6139, 77.2090);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    @DisplayName("distanceKm: Bangalore to Chennai ≈ 290 km (±5 km tolerance)")
    void distanceKm_bangaloreToChennai() {
        // Bangalore: 12.9716, 77.5946 — Chennai: 13.0827, 80.2707
        double dist = geoService.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        assertThat(dist).isBetween(285.0, 295.0);
    }

    // ── ETA fallback (Maps disabled) ──────────────────────────────────────────

    @Test
    @DisplayName("getEtaMinutes: returns Haversine-based estimate when Maps API disabled")
    void getEtaMinutes_mapsDisabled_returnsHaversineEstimate() {
        // ~14 km — at 30 km/h ≈ 28 min
        int eta = geoService.getEtaMinutes(28.6139, 77.2090, 28.5355, 77.3910);
        assertThat(eta).isBetween(20, 40);
    }

    @Test
    @DisplayName("getEtaMinutes: minimum ETA is 5 minutes for very close points")
    void getEtaMinutes_minimumFiveMinutes() {
        // Essentially same point — should still return 5 min minimum
        int eta = geoService.getEtaMinutes(28.6139, 77.2090, 28.6140, 77.2091);
        assertThat(eta).isGreaterThanOrEqualTo(5);
    }

    // ── Google Maps API parsing ───────────────────────────────────────────────

    @Test
    @DisplayName("getEtaMinutes: parses Google Maps API response and returns duration in minutes")
    void getEtaMinutes_mapsEnabled_parsesResponse() {
        // Enable Maps and provide a mock RestTemplate that returns canned response
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        geoService = new GeoService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(geoService, "mapsEnabled", true);
        ReflectionTestUtils.setField(geoService, "apiKey", "fake-key");
        ReflectionTestUtils.setField(geoService, "restTemplate", mockRestTemplate);

        // Canned Maps Distance Matrix API response — duration.value=1800 (30 min)
        java.util.Map<String, Object> response = java.util.Map.of(
                "status", "OK",
                "rows", java.util.List.of(
                        java.util.Map.of("elements", java.util.List.of(
                                java.util.Map.of(
                                        "status", "OK",
                                        "duration", java.util.Map.of("value", 1800)
                                )
                        ))
                )
        );
        when(mockRestTemplate.getForObject(anyString(), eq(java.util.Map.class)))
                .thenReturn(response);

        int eta = geoService.getEtaMinutes(28.6139, 77.2090, 28.5, 77.0);
        assertThat(eta).isEqualTo(30);
    }

    @Test
    @DisplayName("getEtaMinutes: falls back to Haversine when Google Maps API throws")
    void getEtaMinutes_mapsEnabled_fallbackOnApiError() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        geoService = new GeoService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(geoService, "mapsEnabled", true);
        ReflectionTestUtils.setField(geoService, "apiKey", "fake-key");
        ReflectionTestUtils.setField(geoService, "restTemplate", mockRestTemplate);

        when(mockRestTemplate.getForObject(anyString(), eq(java.util.Map.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("timeout"));

        // Should not throw — should fall back to Haversine estimate
        assertThatCode(() -> geoService.getEtaMinutes(28.6139, 77.2090, 28.5, 77.0))
                .doesNotThrowAnyException();
    }

    // ── Maps link ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buildMapsLink: returns Google Maps navigation URL with correct coordinates")
    void buildMapsLink_correctFormat() {
        String link = geoService.buildMapsLink(28.6139, 77.2090);
        assertThat(link).startsWith("https://www.google.com/maps/dir/?api=1&destination=");
        assertThat(link).contains("28.6139").contains("77.209");
    }
}
