package com.nagarro.eclaims.partner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Utility service for geographic distance calculations and Google Maps ETA.
 *
 * Uses the Haversine formula for straight-line (great-circle) distance used
 * in initial DB geo-filter queries.
 *
 * Uses Google Maps Distance Matrix API for driving-time ETA estimation for
 * surveyor assignment and workshop ranking — per architecture design.
 *
 * Set GOOGLE_MAPS_ENABLED=false (and leave GOOGLE_MAPS_API_KEY blank) in
 * local dev to skip Google Maps API calls and fall back to estimated ETA.
 */
@Service
@Slf4j
public class GeoService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final String MAPS_DISTANCE_MATRIX_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";

    private final RestTemplate restTemplate;

    @Value("${eclaims.google-maps.api-key:}")
    private String apiKey;

    @Value("${eclaims.google-maps.enabled:false}")
    private boolean mapsEnabled;

    public GeoService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /**
     * Calculates straight-line (great-circle) distance in kilometres using Haversine.
     * Used for initial DB geo-filter radius queries.
     */
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 100.0) / 100.0;
    }

    /**
     * Estimates driving time in minutes from surveyor/workshop location to accident site.
     * Calls Google Maps Distance Matrix API when enabled; falls back to Haversine estimate.
     *
     * @param originLat       surveyor/workshop latitude
     * @param originLng       surveyor/workshop longitude
     * @param destinationLat  accident site latitude
     * @param destinationLng  accident site longitude
     * @return estimated driving time in minutes
     */
    public int getEtaMinutes(double originLat, double originLng,
                              double destinationLat, double destinationLng) {
        if (!mapsEnabled || apiKey.isBlank()) {
            // Fallback: rough estimate at average city speed of 30 km/h
            double distKm = distanceKm(originLat, originLng, destinationLat, destinationLng);
            int eta = (int) Math.max(5, Math.ceil(distKm / 30.0 * 60));
            log.debug("[GEO] Maps API disabled — Haversine ETA estimate: {} min", eta);
            return eta;
        }

        try {
            String origins = originLat + "," + originLng;
            String destinations = destinationLat + "," + destinationLng;

            String url = UriComponentsBuilder.fromHttpUrl(MAPS_DISTANCE_MATRIX_URL)
                    .queryParam("origins", origins)
                    .queryParam("destinations", destinations)
                    .queryParam("mode", "driving")
                    .queryParam("units", "metric")
                    .queryParam("key", apiKey)
                    .build(false)
                    .toUriString();

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);

            if (response != null && "OK".equals(response.get("status"))) {
                // Navigate: rows[0].elements[0].duration.value (seconds)
                var rows = (java.util.List<?>) response.get("rows");
                var elements = (java.util.List<?>) ((java.util.Map<?, ?>) rows.get(0)).get("elements");
                var element = (java.util.Map<?, ?>) elements.get(0);
                if ("OK".equals(element.get("status"))) {
                    var duration = (java.util.Map<?, ?>) element.get("duration");
                    int durationSeconds = ((Number) duration.get("value")).intValue();
                    int etaMinutes = (int) Math.ceil(durationSeconds / 60.0);
                    log.debug("[GEO] Google Maps ETA: {} min for ({},{}) → ({},{})",
                            etaMinutes, originLat, originLng, destinationLat, destinationLng);
                    return etaMinutes;
                }
            }
        } catch (Exception ex) {
            log.warn("[GEO] Google Maps API call failed — {}, falling back to Haversine", ex.getMessage());
        }

        // Fallback to Haversine if Maps API call fails
        double distKm = distanceKm(originLat, originLng, destinationLat, destinationLng);
        return (int) Math.max(5, Math.ceil(distKm / 30.0 * 60));
    }

    /** Builds a Google Maps navigation deep-link to the given coordinates */
    public String buildMapsLink(double lat, double lng) {
        return String.format("https://www.google.com/maps/dir/?api=1&destination=%s,%s", lat, lng);
    }
}
