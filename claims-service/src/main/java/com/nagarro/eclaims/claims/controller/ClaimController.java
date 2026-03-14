package com.nagarro.eclaims.claims.controller;

import com.nagarro.eclaims.claims.dto.ClaimRequest;
import com.nagarro.eclaims.claims.dto.ClaimResponse;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Claims REST controller. Base path: /api/v1/claims
 * All endpoints require valid Keycloak JWT Bearer token.
 */
@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    /** POST /claims — FNOL submission. Role: CUSTOMER */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ClaimResponse> submit(
            @Valid @RequestBody ClaimRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            claimService.submitClaim(req,
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("phone_number")));
    }

    /** GET /claims/my — customer's own claims with pagination */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<ClaimResponse> myClaims(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return claimService.getByUser(jwt.getSubject(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /** GET /claims/{id} — full detail with history */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADJUSTOR','CASE_MANAGER','SURVEYOR','AUDITOR')")
    public ClaimResponse getById(@PathVariable UUID id) {
        return claimService.getById(id);
    }

    /** GET /claims/number/{number} */
    @GetMapping("/number/{number}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADJUSTOR','CASE_MANAGER','SURVEYOR','AUDITOR')")
    public ClaimResponse getByNumber(@PathVariable String number) {
        return claimService.getByNumber(number);
    }

    /** GET /claims?status=ASSESSED — list for adjustor/case manager */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADJUSTOR','CASE_MANAGER','AUDITOR')")
    public Page<ClaimResponse> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return claimService.getByStatus(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /** POST /claims/{id}/approve — adjustor approval */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADJUSTOR')")
    public ClaimResponse approve(@PathVariable UUID id,
                                  @RequestBody ApproveRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        return claimService.approve(id, body.approvedAmount, body.deductibleAmount,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"), null);
    }

    public record ApproveRequest(BigDecimal approvedAmount, BigDecimal deductibleAmount) {}
}
