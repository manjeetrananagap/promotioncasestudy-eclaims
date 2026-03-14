package com.nagarro.eclaims.partner.repository;

import com.nagarro.eclaims.partner.entity.Surveyor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for surveyors.
 *
 * Geo-radius query uses the Haversine formula implemented in JPQL.
 * Haversine calculates the great-circle distance between two lat/lng points.
 * Formula: d = 2R * arcsin(sqrt(sin²(Δlat/2) + cos(lat1)*cos(lat2)*sin²(Δlng/2)))
 * where R = 6371 km (Earth radius).
 */
@Repository
public interface SurveyorRepository extends JpaRepository<Surveyor, UUID> {

    /**
     * Find available surveyors within a given radius using the Haversine formula.
     * Returns results ordered by distance (nearest first).
     *
     * @param lat       accident latitude
     * @param lng       accident longitude
     * @param radiusKm  search radius in kilometres
     */
    @Query(value = """
        SELECT s.*, (
            6371 * 2 * ASIN(SQRT(
                POWER(SIN(RADIANS(s.base_lat - :lat) / 2), 2) +
                COS(RADIANS(:lat)) * COS(RADIANS(s.base_lat)) *
                POWER(SIN(RADIANS(s.base_lng - :lng) / 2), 2)
            ))
        ) AS distance_km
        FROM surveyors s
        WHERE s.status = 'AVAILABLE'
          AND s.active_claims < s.max_claims
          AND (
            6371 * 2 * ASIN(SQRT(
                POWER(SIN(RADIANS(s.base_lat - :lat) / 2), 2) +
                COS(RADIANS(:lat)) * COS(RADIANS(s.base_lat)) *
                POWER(SIN(RADIANS(s.base_lng - :lng) / 2), 2)
            ))
          ) <= :radiusKm
        ORDER BY distance_km ASC
        """, nativeQuery = true)
    List<Surveyor> findAvailableWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);

    /** Atomically increment active_claims and mark ASSIGNED */
    @Modifying
    @Query("UPDATE Surveyor s SET s.status = 'ASSIGNED', s.activeClaims = s.activeClaims + 1 WHERE s.id = :id")
    void assignSurveyor(@Param("id") UUID id);

    /** Release surveyor back to AVAILABLE when assessment is submitted */
    @Modifying
    @Query("UPDATE Surveyor s SET s.status = 'AVAILABLE', s.activeClaims = GREATEST(0, s.activeClaims - 1) WHERE s.id = :id")
    void releaseSurveyor(@Param("id") UUID id);
}
