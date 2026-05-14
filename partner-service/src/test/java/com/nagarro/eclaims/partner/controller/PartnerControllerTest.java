package com.nagarro.eclaims.partner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagarro.eclaims.partner.config.TestSecurityConfig;
import com.nagarro.eclaims.partner.dto.WorkshopDto;
import com.nagarro.eclaims.partner.service.PartnerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link PartnerController}.
 * Tests REST contract, security enforcement, and request/response serialisation.
 */
@WebMvcTest(PartnerController.class)
@Import(TestSecurityConfig.class)
@org.springframework.test.context.ActiveProfiles("test")
class PartnerControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean PartnerService partnerService;

    // ── GET /partner/workshops ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /partner/workshops — 200 OK with list of nearby workshops")
    void findWorkshops_returns200() throws Exception {
        WorkshopDto dto = buildWorkshopDto();
        when(partnerService.findNearbyWorkshops(any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/partner/workshops")
                        .param("lat", "28.6139")
                        .param("lng", "77.2090")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dto.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Test Workshop"))
                .andExpect(jsonPath("$[0].distanceKm").value(12.3));
    }

    @Test
    @DisplayName("GET /partner/workshops — 200 OK returns empty array when none found")
    void findWorkshops_emptyList() throws Exception {
        when(partnerService.findNearbyWorkshops(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/partner/workshops")
                        .param("lat", "0.0")
                        .param("lng", "0.0")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /partner/workshops — 401 UNAUTHORIZED without JWT")
    void findWorkshops_noToken_returns401() throws Exception {
        mockMvc.perform(get("/partner/workshops")
                        .param("lat", "28.6139")
                        .param("lng", "77.2090"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /partner/workshops — 400 BAD REQUEST when lat param missing")
    void findWorkshops_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/partner/workshops")
                        .param("lng", "77.2090")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /partner/claims/{claimId}/assign-workshop ───────────────────────

    @Test
    @DisplayName("POST /partner/claims/{id}/assign-workshop — 202 ACCEPTED for valid request")
    void assignWorkshop_returns202() throws Exception {
        UUID claimId   = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();

        PartnerController.AssignWorkshopRequest req = new PartnerController.AssignWorkshopRequest(
                "CLM-2025-000001", workshopId,
                "customer@test.com", "+919876543210",
                LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/partner/claims/{id}/assign-workshop", claimId)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /partner/work-orders/{id}/milestone — 403 FORBIDDEN for CUSTOMER role")
    void updateMilestone_customerRole_returns403() throws Exception {
        UUID claimId = UUID.randomUUID();
        PartnerController.MilestoneRequest req = new PartnerController.MilestoneRequest(
                "PAINT_COMPLETE", "staff-user");

        mockMvc.perform(post("/partner/work-orders/{id}/milestone", claimId)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /partner/work-orders/{id}/milestone — 202 ACCEPTED for WORKSHOP_PARTNER role")
    void updateMilestone_workshopRole_returns202() throws Exception {
        UUID claimId = UUID.randomUUID();
        PartnerController.MilestoneRequest req = new PartnerController.MilestoneRequest(
                "PAINT_COMPLETE", "staff-user");

        mockMvc.perform(post("/partner/work-orders/{id}/milestone", claimId)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WORKSHOP_PARTNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private WorkshopDto buildWorkshopDto() {
        return WorkshopDto.builder()
                .id(UUID.randomUUID())
                .name("Test Workshop")
                .address("123 Workshop St")
                .city("New Delhi")
                .lat(new BigDecimal("28.55"))
                .lng(new BigDecimal("77.15"))
                .phone("+911234567890")
                .slaScore(new BigDecimal("95"))
                .distanceKm(12.3)
                .availableSlots(4)
                .mapsLink("https://maps.google.com/?q=28.55,77.15")
                .build();
    }
}
