package com.nagarro.eclaims.partner.service;

import com.nagarro.eclaims.events.ClaimValidatedEvent;
import com.nagarro.eclaims.partner.dto.WorkshopDto;
import com.nagarro.eclaims.partner.entity.Surveyor;
import com.nagarro.eclaims.partner.entity.Workshop;
import com.nagarro.eclaims.partner.repository.SurveyorRepository;
import com.nagarro.eclaims.partner.repository.WorkOrderRepository;
import com.nagarro.eclaims.partner.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PartnerService}.
 * Mocks all repositories, GeoService, and KafkaTemplate.
 */
@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock private SurveyorRepository  surveyorRepository;
    @Mock private WorkshopRepository  workshopRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private GeoService          geoService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private PartnerService partnerService;

    @BeforeEach
    void injectValues() {
        ReflectionTestUtils.setField(partnerService, "surveyorRadiusKm",     25.0);
        ReflectionTestUtils.setField(partnerService, "surveyorMaxRadiusKm",  50.0);
        ReflectionTestUtils.setField(partnerService, "workshopRadiusKm",     30.0);
        ReflectionTestUtils.setField(partnerService, "topicSurveyorAssigned",  "surveyor.assigned");
        ReflectionTestUtils.setField(partnerService, "topicWorkshopAssigned",  "workshop.assigned");
        ReflectionTestUtils.setField(partnerService, "topicRepairStatus",      "repair.status.updated");
        ReflectionTestUtils.setField(partnerService, "topicRepairCompleted",   "repair.completed");

        lenient().doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── assignSurveyor ────────────────────────────────────────────────────────

    @Test
    @DisplayName("assignSurveyor: assigns nearest surveyor and publishes event")
    void assignSurveyor_success() {
        ClaimValidatedEvent event = buildValidatedEvent();
        Surveyor surveyor = buildSurveyor();

        when(surveyorRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), eq(25.0)))
                .thenReturn(List.of(surveyor));
        when(geoService.distanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(8.5);
        when(geoService.getEtaMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(20);
        when(geoService.buildMapsLink(anyDouble(), anyDouble()))
                .thenReturn("https://maps.google.com/?q=28.6,77.2");

        partnerService.assignSurveyor(event);

        verify(surveyorRepository).assignSurveyor(surveyor.getId());
        verify(kafkaTemplate).send(eq("surveyor.assigned"), anyString(), any());
    }

    @Test
    @DisplayName("assignSurveyor: expands radius when no surveyors in primary radius")
    void assignSurveyor_expandsRadius() {
        ClaimValidatedEvent event = buildValidatedEvent();
        Surveyor surveyor = buildSurveyor();

        when(surveyorRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), eq(25.0)))
                .thenReturn(List.of());   // no surveyors in 25km
        when(surveyorRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), eq(50.0)))
                .thenReturn(List.of(surveyor));  // found at 50km
        when(geoService.distanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(38.0);
        when(geoService.getEtaMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(75);
        when(geoService.buildMapsLink(anyDouble(), anyDouble()))
                .thenReturn("https://maps.google.com/?q=28.6,77.2");

        partnerService.assignSurveyor(event);

        verify(surveyorRepository).findAvailableWithinRadius(anyDouble(), anyDouble(), eq(50.0));
        verify(surveyorRepository).assignSurveyor(surveyor.getId());
    }

    @Test
    @DisplayName("assignSurveyor: no-op when no surveyors in both radii")
    void assignSurveyor_noSurveyorsFound_doesNothing() {
        ClaimValidatedEvent event = buildValidatedEvent();

        when(surveyorRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        partnerService.assignSurveyor(event);

        verify(surveyorRepository, never()).assignSurveyor(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("assignSurveyor: no-op when event has no GPS coordinates")
    void assignSurveyor_noGpsCoords_doesNothing() {
        ClaimValidatedEvent event = ClaimValidatedEvent.builder()
                .claimId(UUID.randomUUID())
                .claimNumber("CLM-2025-000001")
                .accidentLat(null)
                .accidentLng(null)
                .build();

        partnerService.assignSurveyor(event);

        verifyNoInteractions(surveyorRepository, kafkaTemplate);
    }

    // ── findNearbyWorkshops ───────────────────────────────────────────────────

    @Test
    @DisplayName("findNearbyWorkshops: returns filtered and mapped workshops")
    void findNearbyWorkshops_returnsWorkshops() {
        Workshop w = buildWorkshop(true, 5);
        when(workshopRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), eq(30.0)))
                .thenReturn(List.of(w));
        when(geoService.distanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(12.3);
        when(geoService.buildMapsLink(anyDouble(), anyDouble()))
                .thenReturn("https://maps.google.com/?q=28.5,77.1");

        List<WorkshopDto> result = partnerService.findNearbyWorkshops(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Workshop");
        assertThat(result.get(0).getDistanceKm()).isEqualTo(12.3);
    }

    @Test
    @DisplayName("findNearbyWorkshops: excludes workshops with expired certification")
    void findNearbyWorkshops_excludesExpiredCertification() {
        Workshop valid   = buildWorkshop(true, 3);
        Workshop expired = buildWorkshop(false, 5);
        when(workshopRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(valid, expired));
        when(geoService.distanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(5.0);
        when(geoService.buildMapsLink(anyDouble(), anyDouble()))
                .thenReturn("https://maps.google.com/");

        List<WorkshopDto> result = partnerService.findNearbyWorkshops(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"));

        // Only the valid-certification workshop should be in result
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findNearbyWorkshops: returns empty list when none in radius")
    void findNearbyWorkshops_emptyResult() {
        when(workshopRepository.findAvailableWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        List<WorkshopDto> result = partnerService.findNearbyWorkshops(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"));

        assertThat(result).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ClaimValidatedEvent buildValidatedEvent() {
        return ClaimValidatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(UUID.randomUUID())
                .claimNumber("CLM-2025-000001")
                .policyHolderEmail("customer@test.com")
                .accidentLat(new BigDecimal("28.6139"))
                .accidentLng(new BigDecimal("77.2090"))
                .build();
    }

    private Surveyor buildSurveyor() {
        return Surveyor.builder()
                .id(UUID.randomUUID())
                .name("Arun Kumar")
                .email("arun@surveyor.com")
                .phone("+919876543210")
                .baseLat(new BigDecimal("28.55"))
                .baseLng(new BigDecimal("77.15"))
                .status("AVAILABLE")
                .build();
    }

    private Workshop buildWorkshop(boolean certificationValid, int availableSlots) {
        return Workshop.builder()
                .id(UUID.randomUUID())
                .name("Test Workshop")
                .address("123 Workshop St")
                .city("New Delhi")
                .lat(new BigDecimal("28.55"))
                .lng(new BigDecimal("77.15"))
                .phone("+911234567890")
                .slaScore(new BigDecimal("95"))
                .weeklyCapacity(10)
                .currentLoad(10 - availableSlots)
                .status("ACTIVE")
                .certificationExpiry(certificationValid
                        ? LocalDate.now().plusYears(1)
                        : LocalDate.now().minusDays(1))
                .build();
    }
}
