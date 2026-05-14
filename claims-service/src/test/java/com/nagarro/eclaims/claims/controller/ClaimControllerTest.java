package com.nagarro.eclaims.claims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagarro.eclaims.claims.config.TestSecurityConfig;
import com.nagarro.eclaims.claims.dto.ClaimRequest;
import com.nagarro.eclaims.claims.dto.ClaimResponse;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.exception.ClaimNotFoundException;
import com.nagarro.eclaims.claims.service.ClaimService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ClaimController}.
 * Loads only the web layer — mocks ClaimService.
 * Uses Spring Security JWT post-processor so @PreAuthorize is enforced.
 */
@WebMvcTest(ClaimController.class)
@Import(TestSecurityConfig.class)
@org.springframework.test.context.ActiveProfiles("test")
class ClaimControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean ClaimService claimService;

    // ── POST /claims ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /claims — 201 CREATED with valid request and CUSTOMER role")
    void submit_returns201() throws Exception {
        ClaimResponse response = buildResponse(ClaimStatus.VALIDATED);
        when(claimService.submitClaim(any(), anyString(), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/claims")
                        .with(jwt().jwt(j -> j.subject("user-001").claim("email", "r@test.com"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.claimNumber").value("CLM-2025-000001"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    @DisplayName("POST /claims — 403 FORBIDDEN when caller has ADJUSTOR role (not CUSTOMER)")
    void submit_403_wrongRole() throws Exception {
        // Provide ROLE_ADJUSTOR directly — no ROLE_CUSTOMER → @PreAuthorize blocks at method level.
        // Note: @WebMvcTest + @EnableMethodSecurity (from SecurityConfig) enforces this when
        // authorities are set explicitly via the jwt() post-processor.
        mockMvc.perform(post("/claims")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADJUSTOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(buildRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /claims — 400 BAD REQUEST when policyId is blank")
    void submit_400_missingPolicyId() throws Exception {
        ClaimRequest bad = buildRequest();
        bad.setPolicyId("");

        mockMvc.perform(post("/claims")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /claims — 401/403 UNAUTHORIZED when no JWT token")
    void submit_401_noToken() throws Exception {
        mockMvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(buildRequest())))
                .andExpect(status().is4xxClientError()); // 401 or 403 depending on security config
    }

    // ── GET /claims/{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /claims/{id} — 200 OK for existing claim")
    void getById_returns200() throws Exception {
        ClaimResponse response = buildResponse(ClaimStatus.SUBMITTED);
        when(claimService.getById(response.getId())).thenReturn(response);

        mockMvc.perform(get("/claims/" + response.getId())
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADJUSTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("GET /claims/{id} — 404 NOT FOUND for unknown claim")
    void getById_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(claimService.getById(unknown))
                .thenThrow(new ClaimNotFoundException("Claim not found: " + unknown));

        mockMvc.perform(get("/claims/" + unknown)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADJUSTOR"))))
                .andExpect(status().isNotFound());
    }

    // ── GET /claims/my ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /claims/my — 200 OK paged list for CUSTOMER")
    void myClaims_returns200() throws Exception {
        ClaimResponse response = buildResponse(ClaimStatus.SUBMITTED);
        when(claimService.getByUser(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/claims/my")
                        .with(jwt().jwt(j -> j.subject("user-001"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.getId().toString()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ClaimResponse buildResponse(ClaimStatus status) {
        return ClaimResponse.builder()
                .id(UUID.randomUUID())
                .claimNumber("CLM-2025-000001")
                .policyId("POL-001")
                .policyHolderName("Rajesh Verma")
                .vehicleReg("DL01AB1234")
                .status(status)
                .incidentDate(LocalDate.now().minusDays(1))
                .createdAt(LocalDateTime.now())
                .statusHistory(List.of())
                .build();
    }

    private ClaimRequest buildRequest() {
        return ClaimRequest.builder()
                .policyId("POL-001")
                .vehicleReg("DL01AB1234")
                .vehicleMake("Toyota")
                .vehicleModel("Innova")
                .accidentLat(new BigDecimal("28.6139"))
                .accidentLng(new BigDecimal("77.2090"))
                .accidentAddress("New Delhi")
                .incidentDate(LocalDate.now().minusDays(1))
                .incidentDescription("Rear-end collision on NH-48")
                .build();
    }
}
