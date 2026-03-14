package com.nagarro.eclaims.document.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nagarro.eclaims.document.entity.Document;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Response DTO for document metadata — returned after upload and in document lists */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentResponse {

    private UUID   id;
    private UUID   claimId;
    private String documentType;
    private String originalName;
    private String contentType;
    private Long   fileSizeBytes;

    /** 1-hour pre-signed URL for direct browser download */
    private String storageUrl;

    /** EXIF GPS coordinates (photos only) */
    private BigDecimal photoLat;
    private BigDecimal photoLng;

    private String uploadedByUserId;
    private String uploadSource;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public static DocumentResponse fromEntity(Document d) {
        return DocumentResponse.builder()
                .id(d.getId())
                .claimId(d.getClaimId())
                .documentType(d.getDocumentType())
                .originalName(d.getOriginalName())
                .contentType(d.getContentType())
                .fileSizeBytes(d.getFileSizeBytes())
                .storageUrl(d.getStorageUrl())
                .photoLat(d.getPhotoLat())
                .photoLng(d.getPhotoLng())
                .uploadedByUserId(d.getUploadedByUserId())
                .uploadSource(d.getUploadSource())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
