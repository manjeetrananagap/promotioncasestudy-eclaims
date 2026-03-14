package com.nagarro.eclaims.partner.service;

import com.nagarro.eclaims.events.*;
import com.nagarro.eclaims.partner.dto.WorkshopDto;
import com.nagarro.eclaims.partner.entity.Surveyor;
import com.nagarro.eclaims.partner.entity.WorkOrder;
import com.nagarro.eclaims.partner.entity.Workshop;
import com.nagarro.eclaims.partner.repository.SurveyorRepository;
import com.nagarro.eclaims.partner.repository.WorkOrderRepository;
import com.nagarro.eclaims.partner.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Partner Service — core geo-matching engine.
 *
 * Surveyor Assignment (triggered by claim.validated event):
 *   1. Query partner_db for AVAILABLE surveyors within 25km (Haversine)
 *   2. If none found, expand to 50km
 *   3. Select nearest available surveyor
 *   4. Atomically mark surveyor ASSIGNED in partner_db
 *   5. Publish surveyor.assigned Kafka event
 *
 * Workshop Assignment (triggered by claim.approved event):
 *   1. Query partner_db for ACTIVE workshops within 30km
 *   2. Filter: valid certification, capacity available
 *   3. Return ranked list to customer portal
 *   4. Customer selects → create WorkOrder → publish workshop.assigned
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerService {

    private final SurveyorRepository  surveyorRepository;
    private final WorkshopRepository  workshopRepository;
    private final WorkOrderRepository workOrderRepository;
    private final GeoService          geoService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${eclaims.geo.surveyor-radius-km:25}")     private double surveyorRadiusKm;
    @Value("${eclaims.geo.surveyor-max-radius-km:50}") private double surveyorMaxRadiusKm;
    @Value("${eclaims.geo.workshop-radius-km:30}")     private double workshopRadiusKm;
    @Value("${eclaims.kafka.topics.surveyor-assigned}") private String topicSurveyorAssigned;
    @Value("${eclaims.kafka.topics.workshop-assigned}") private String topicWorkshopAssigned;
    @Value("${eclaims.kafka.topics.repair-status-updated}") private String topicRepairStatus;
    @Value("${eclaims.kafka.topics.repair-completed}")  private String topicRepairCompleted;

    // ── Surveyor assignment ────────────────────────────────────────────────────

    /**
     * Auto-assigns nearest available surveyor to a validated claim.
     * Called when claim.validated event is consumed.
     */
    @Transactional
    public void assignSurveyor(ClaimValidatedEvent event) {
        if (event.getAccidentLat() == null || event.getAccidentLng() == null) {
            log.warn("No GPS coords on claim {} — cannot geo-assign surveyor", event.getClaimNumber());
            return;
        }

        double lat = event.getAccidentLat().doubleValue();
        double lng = event.getAccidentLng().doubleValue();

        log.info("Geo-searching surveyors within {}km of ({},{}) for claim {}",
                surveyorRadiusKm, lat, lng, event.getClaimNumber());

        // First pass: primary radius
        List<Surveyor> candidates = surveyorRepository.findAvailableWithinRadius(lat, lng, surveyorRadiusKm);

        // Auto-expand radius if no candidates found
        if (candidates.isEmpty()) {
            log.info("No surveyors within {}km — expanding to {}km", surveyorRadiusKm, surveyorMaxRadiusKm);
            candidates = surveyorRepository.findAvailableWithinRadius(lat, lng, surveyorMaxRadiusKm);
        }

        if (candidates.isEmpty()) {
            log.warn("No available surveyors found within {}km for claim {}", surveyorMaxRadiusKm, event.getClaimNumber());
            return;
        }

        // Select nearest (results already ordered by distance ASC from DB query)
        Surveyor selected = candidates.get(0);
        double distKm = geoService.distanceKm(lat, lng,
                selected.getBaseLat().doubleValue(), selected.getBaseLng().doubleValue());

        log.info("Assigning surveyor {} ({}) — {}km away — to claim {}",
                selected.getName(), selected.getId(), distKm, event.getClaimNumber());

        // Atomically mark surveyor as ASSIGNED in DB
        surveyorRepository.assignSurveyor(selected.getId());

        // Publish event — Notification Service sends email/SMS to customer and surveyor
        SurveyorAssignedEvent assignedEvent = SurveyorAssignedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(event.getClaimId())
                .claimNumber(event.getClaimNumber())
                .policyHolderEmail(event.getPolicyHolderEmail())
                .surveyorId(selected.getId())
                .surveyorName(selected.getName())
                .surveyorEmail(selected.getEmail())
                .surveyorPhone(selected.getPhone())
                .estimatedArrival("Within " + Math.max(1, (int)(distKm / 30 * 60)) + " minutes")
                .mapsNavigationLink(geoService.buildMapsLink(lat, lng))
                .build();

        kafkaTemplate.send(topicSurveyorAssigned, event.getClaimId().toString(), assignedEvent);
        log.info("Published surveyor.assigned for claim {}", event.getClaimNumber());
    }

    /** Release surveyor back to AVAILABLE after assessment submitted */
    @Transactional
    public void releaseSurveyor(java.util.UUID surveyorId) {
        surveyorRepository.releaseSurveyor(surveyorId);
        log.info("Released surveyor {} back to AVAILABLE", surveyorId);
    }

    // ── Workshop search (called by REST from customer portal) ─────────────────

    /**
     * Returns geo-ranked workshops for customer selection after claim approval.
     * Results are filtered: active, valid certification, capacity available.
     */
    @Transactional(readOnly = true)
    public List<WorkshopDto> findNearbyWorkshops(BigDecimal lat, BigDecimal lng) {
        double latD = lat.doubleValue();
        double lngD = lng.doubleValue();

        List<Workshop> workshops = workshopRepository.findAvailableWithinRadius(latD, lngD, workshopRadiusKm);

        return workshops.stream()
                .filter(Workshop::isCertificationValid)
                .map(w -> WorkshopDto.builder()
                        .id(w.getId())
                        .name(w.getName())
                        .address(w.getAddress())
                        .city(w.getCity())
                        .lat(w.getLat())
                        .lng(w.getLng())
                        .phone(w.getPhone())
                        .repairTypes(w.getRepairTypes())
                        .vehicleBrands(w.getVehicleBrands())
                        .slaScore(w.getSlaScore())
                        .distanceKm(geoService.distanceKm(latD, lngD,
                                w.getLat().doubleValue(), w.getLng().doubleValue()))
                        .availableSlots(w.getWeeklyCapacity() - w.getCurrentLoad())
                        .mapsLink(geoService.buildMapsLink(
                                w.getLat().doubleValue(), w.getLng().doubleValue()))
                        .build())
                .collect(Collectors.toList());
    }

    // ── Workshop assignment (customer selects from portal) ────────────────────

    @Transactional
    public void assignWorkshop(UUID claimId, String claimNumber, UUID workshopId,
                                String policyHolderEmail, String policyHolderPhone,
                                LocalDateTime appointmentAt) {

        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new RuntimeException("Workshop not found: " + workshopId));

        // Create work order
        WorkOrder order = WorkOrder.builder()
                .claimId(claimId)
                .claimNumber(claimNumber)
                .workshop(workshop)
                .status("NEW")
                .appointmentAt(appointmentAt)
                .build();

        workOrderRepository.save(order);
        workshopRepository.incrementLoad(workshopId);

        // Publish workshop.assigned event
        WorkshopAssignedEvent event = WorkshopAssignedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(claimId)
                .claimNumber(claimNumber)
                .policyHolderEmail(policyHolderEmail)
                .policyHolderPhone(policyHolderPhone)
                .workshopId(workshop.getId())
                .workshopName(workshop.getName())
                .workshopAddress(workshop.getAddress())
                .workshopPhone(workshop.getPhone())
                .appointmentDateTime(appointmentAt)
                .estimatedCompletionDays("5-7")
                .build();

        kafkaTemplate.send(topicWorkshopAssigned, claimId.toString(), event);
        log.info("Published workshop.assigned — claim:{} workshop:{}", claimNumber, workshop.getName());
    }

    // ── Repair milestone update ───────────────────────────────────────────────

    @Transactional
    public void updateRepairMilestone(UUID claimId, String milestone,
                                       String workshopStaffName) {

        WorkOrder order = workOrderRepository.findByClaimIdAndStatusNot(claimId, "CANCELLED")
                .orElseThrow(() -> new RuntimeException("WorkOrder not found for claim: " + claimId));

        order.setCurrentMilestone(milestone);

        boolean completed = "READY_FOR_PICKUP".equals(milestone);
        if (completed) {
            order.setStatus("COMPLETED");
            order.setCompletedAt(LocalDateTime.now());
        }

        workOrderRepository.save(order);

        String milestoneLabel = toMilestoneLabel(milestone);

        if (completed) {
            // Publish repair.completed — triggers payment flow
            RepairCompletedEvent event = RepairCompletedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .occurredAt(LocalDateTime.now())
                    .claimId(claimId)
                    .claimNumber(order.getClaimNumber())
                    .workshopId(order.getWorkshop().getId())
                    .workshopName(order.getWorkshop().getName())
                    .build();
            kafkaTemplate.send(topicRepairCompleted, claimId.toString(), event);
        } else {
            // Publish repair.status.updated — Notification sends milestone SMS to customer
            RepairStatusUpdatedEvent event = RepairStatusUpdatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .occurredAt(LocalDateTime.now())
                    .claimId(claimId)
                    .claimNumber(order.getClaimNumber())
                    .workshopName(order.getWorkshop().getName())
                    .milestone(milestone)
                    .milestoneLabel(milestoneLabel)
                    .estimatedCompletionDate("Approx. 5-7 business days from intake")
                    .build();
            kafkaTemplate.send(topicRepairStatus, claimId.toString(), event);
        }
    }

    private String toMilestoneLabel(String milestone) {
        return switch (milestone) {
            case "VEHICLE_RECEIVED"   -> "Vehicle Received at Workshop";
            case "DISASSEMBLY"        -> "Disassembly in Progress";
            case "PARTS_ORDERED"      -> "Spare Parts Ordered";
            case "REPAIR_IN_PROGRESS" -> "Repair Work in Progress";
            case "QUALITY_CHECK"      -> "Quality Check";
            case "READY_FOR_PICKUP"   -> "Vehicle Ready for Pickup";
            default                   -> milestone;
        };
    }
}
