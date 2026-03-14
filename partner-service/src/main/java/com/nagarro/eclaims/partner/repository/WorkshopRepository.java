package com.nagarro.eclaims.partner.repository;

import com.nagarro.eclaims.partner.entity.Workshop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, UUID> {

    /**
     * Find ACTIVE workshops within radius, ordered nearest first.
     * Applies Haversine formula (same as SurveyorRepository).
     */
    @Query(value = """
        SELECT w.*, (
            6371 * 2 * ASIN(SQRT(
                POWER(SIN(RADIANS(w.lat - :lat) / 2), 2) +
                COS(RADIANS(:lat)) * COS(RADIANS(w.lat)) *
                POWER(SIN(RADIANS(w.lng - :lng) / 2), 2)
            ))
        ) AS distance_km
        FROM workshops w
        WHERE w.status = 'ACTIVE'
          AND w.current_load < w.weekly_capacity
          AND (w.certification_expiry IS NULL OR w.certification_expiry >= CURRENT_DATE)
          AND (
            6371 * 2 * ASIN(SQRT(
                POWER(SIN(RADIANS(w.lat - :lat) / 2), 2) +
                COS(RADIANS(:lat)) * COS(RADIANS(w.lat)) *
                POWER(SIN(RADIANS(w.lng - :lng) / 2), 2)
            ))
          ) <= :radiusKm
        ORDER BY distance_km ASC
        LIMIT 10
        """, nativeQuery = true)
    List<Workshop> findAvailableWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);

    @Modifying
    @Query("UPDATE Workshop w SET w.currentLoad = w.currentLoad + 1 WHERE w.id = :id")
    void incrementLoad(@Param("id") UUID id);
}
