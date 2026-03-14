package com.nagarro.eclaims.partner.service;

import org.springframework.stereotype.Service;

/**
 * Utility service for geographic distance calculations.
 *
 * Uses the Haversine formula to calculate the great-circle distance
 * between two points on Earth given their latitude/longitude coordinates.
 *
 * This is used for:
 * - Displaying distance to customer on workshop selection map
 * - Sorting results by proximity when DB query returns candidates
 * - The primary geo-filtering is done in the DB query (SurveyorRepository / WorkshopRepository)
 */
@Service
public class GeoService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculates straight-line (great-circle) distance in kilometres.
     *
     * @param lat1  latitude of point 1 in degrees
     * @param lng1  longitude of point 1 in degrees
     * @param lat2  latitude of point 2 in degrees
     * @param lng2  longitude of point 2 in degrees
     * @return distance in kilometres, rounded to 2 decimal places
     */
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;

        return Math.round(distanceKm * 100.0) / 100.0;
    }

    /** Builds a Google Maps navigation deep-link to the given coordinates */
    public String buildMapsLink(double lat, double lng) {
        return String.format("https://www.google.com/maps/dir/?api=1&destination=%s,%s", lat, lng);
    }
}
