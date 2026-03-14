package com.nagarro.eclaims.document.controller;

import com.nagarro.eclaims.document.dto.DocumentResponse;
import com.nagarro.eclaims.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for document upload and retrieval.
 *
 * <p>Base path: {@code /api/v1/documents}</p>
 *
 * <p>All endpoints require a valid Keycloak JWT.
 * Uploading requires CUSTOMER, SURVEYOR, or ADJUSTOR role.
 * Listing requires any authenticated role.</p>
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * POST /documents/upload
     * Upload a single file for a specific claim.
     *
     * Form params:
     *   claimId      — UUID of the claim
     *   documentType — ACCIDENT_PHOTO | POLICE_REPORT | VEHICLE_RC | etc.
     *   file         — The file to upload (max 20MB)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER')")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam UUID           claimId,
            @RequestParam String         documentType,
            @RequestParam MultipartFile  file,
            @AuthenticationPrincipal Jwt jwt) {

        String uploadedBy = jwt.getSubject();
        // Determine source based on JWT role
        String uploadSource = resolveUploadSource(jwt);

        log.info("Upload request — claimId:{}, type:{}, file:{} ({} bytes)",
                claimId, documentType, file.getOriginalFilename(), file.getSize());

        DocumentResponse response = documentService.upload(
                claimId, documentType, file, uploadedBy, uploadSource);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /documents/claim/{claimId}
     * List all documents for a claim. Returns metadata + pre-signed download URLs.
     */
    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    public ResponseEntity<List<DocumentResponse>> getByClaimId(@PathVariable UUID claimId) {
        return ResponseEntity.ok(documentService.getDocumentsByClaim(claimId));
    }

    /**
     * GET /documents/claim/{claimId}/type/{documentType}
     * Get documents of a specific type for a claim.
     */
    @GetMapping("/claim/{claimId}/type/{documentType}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    public ResponseEntity<List<DocumentResponse>> getByClaimAndType(
            @PathVariable UUID claimId,
            @PathVariable String documentType) {
        return ResponseEntity.ok(documentService.getByClaimAndType(claimId, documentType));
    }

    /**
     * GET /documents/{id}/download
     * Stream document binary directly from MinIO. Used for inline viewing in browser.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(
            @PathVariable UUID id) {
        InputStream stream = documentService.downloadStream(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveUploadSource(Jwt jwt) {
        var roles = jwt.getClaimAsMap("realm_access");
        if (roles == null) return "UNKNOWN";
        var roleList = (java.util.List<?>) roles.get("roles");
        if (roleList == null) return "UNKNOWN";
        if (roleList.contains("SURVEYOR"))   return "SURVEYOR_APP";
        if (roleList.contains("ADJUSTOR"))   return "ADJUSTOR_PORTAL";
        if (roleList.contains("CUSTOMER"))   return "CUSTOMER_PORTAL";
        return "SYSTEM";
    }
}
