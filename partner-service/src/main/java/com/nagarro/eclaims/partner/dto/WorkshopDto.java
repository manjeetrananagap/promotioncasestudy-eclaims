package com.nagarro.eclaims.partner.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Workshop search result returned to customer portal for map display. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkshopDto {
    private UUID       id;
    private String     name;
    private String     address;
    private String     city;
    private BigDecimal lat;
    private BigDecimal lng;
    private String     phone;
    private String     repairTypes;
    private String     vehicleBrands;
    private BigDecimal slaScore;
    private double     distanceKm;      // Calculated by GeoService
    private int        availableSlots;
    private String     mapsLink;        // Google Maps deep-link
}
