package com.nagarro.eclaims.partner.controller;

import com.nagarro.eclaims.partner.dto.WorkshopDto;
import com.nagarro.eclaims.partner.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Partner REST controller.
 * Base path: /api/v1/partner
 */
@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    /**
     * GET /partner/workshops?lat=28.61&lng=77.20
     * Returns geo-ranked workshops near the given coordinates.
     * Called by customer portal after claim approval to show workshop map.
     */
    @GetMapping("/workshops")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADJUSTOR','CASE_MANAGER')")
    public List<WorkshopDto> findWorkshops(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng) {
        return partnerService.findNearbyWorkshops(lat, lng);
    }

    /**
     * POST /partner/claims/{claimId}/assign-workshop
     * Customer selects a workshop from the map — creates work order.
     */
    @PostMapping("/claims/{claimId}/assign-workshop")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADJUSTOR','CASE_MANAGER')")
    public ResponseEntity<Void> assignWorkshop(
            @PathVariable UUID claimId,
            @RequestBody AssignWorkshopRequest req) {

        partnerService.assignWorkshop(
                claimId, req.claimNumber(), req.workshopId(),
                req.policyHolderEmail(), req.policyHolderPhone(),
                req.appointmentAt());

        return ResponseEntity.accepted().build();
    }

    /**
     * POST /partner/work-orders/{claimId}/milestone
     * Workshop staff updates repair progress milestone.
     * Role: WORKSHOP_PARTNER
     */
    @PostMapping("/work-orders/{claimId}/milestone")
    @PreAuthorize("hasAnyRole('WORKSHOP_PARTNER','CASE_MANAGER')")
    public ResponseEntity<Void> updateMilestone(
            @PathVariable UUID claimId,
            @RequestBody MilestoneRequest req) {

        partnerService.updateRepairMilestone(claimId, req.milestone(), req.updatedBy());
        return ResponseEntity.accepted().build();
    }

    public record AssignWorkshopRequest(
            String claimNumber, UUID workshopId,
            String policyHolderEmail, String policyHolderPhone,
            LocalDateTime appointmentAt) {}

    public record MilestoneRequest(String milestone, String updatedBy) {}
}
