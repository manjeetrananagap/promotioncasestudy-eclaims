package com.nagarro.eclaims.document.service;

import com.nagarro.eclaims.document.dto.DocumentResponse;
import com.nagarro.eclaims.document.entity.Document;
import com.nagarro.eclaims.document.repository.DocumentRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles file storage in MinIO and metadata persistence in PostgreSQL.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Receive MultipartFile from REST controller</li>
 *   <li>Build a namespaced MinIO object key: {@code {claimId}/{docType}/{uuid}.{ext}}</li>
 *   <li>Stream file bytes to MinIO (no temp file on disk)</li>
 *   <li>Persist document metadata to PostgreSQL via JPA</li>
 *   <li>Return document response with pre-signed URL</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MinioClient minioClient;

    @Value("${eclaims.minio.bucket-documents}")
    private String bucketDocuments;

    @Value("${eclaims.minio.bucket-photos}")
    private String bucketPhotos;

    @Value("${eclaims.minio.bucket-reports}")
    private String bucketReports;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads a file to MinIO and saves metadata to PostgreSQL.
     *
     * @param claimId       UUID of the claim this document belongs to
     * @param documentType  document classification (ACCIDENT_PHOTO, POLICE_REPORT, etc.)
     * @param file          multipart file from the HTTP request
     * @param uploadedBy    Keycloak user ID of the uploader
     * @param uploadSource  CUSTOMER_PORTAL | SURVEYOR_APP | ADJUSTOR_PORTAL
     */
    @Transactional
    public DocumentResponse upload(UUID claimId, String documentType,
                                    MultipartFile file, String uploadedBy, String uploadSource) {

        // Determine bucket based on document type
        String bucket = resolveBucket(documentType);

        // Build namespaced object key — prevents collisions, easy to list per claim
        String extension  = getExtension(file.getOriginalFilename());
        String objectKey  = "%s/%s/%s%s".formatted(claimId, documentType, UUID.randomUUID(), extension);

        log.info("Uploading {} → s3://{}/{} ({} bytes)",
                documentType, bucket, objectKey, file.getSize());

        // Stream directly to MinIO — no temp file created on local disk
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
        } catch (Exception e) {
            log.error("MinIO upload failed for claimId={}, type={}: {}", claimId, documentType, e.getMessage());
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }

        // Calculate 7-year retention date for regulatory compliance
        LocalDate retentionUntil = LocalDate.now().plusYears(7);

        // Build and persist metadata to document_db via JPA
        Document document = Document.builder()
                .claimId(claimId)
                .documentType(documentType)
                .originalName(file.getOriginalFilename())
                .storageBucket(bucket)
                .storageKey(objectKey)
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .uploadedByUserId(uploadedBy)
                .uploadSource(uploadSource)
                .retentionUntil(retentionUntil)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document metadata saved — id:{}, claimId:{}", saved.getId(), claimId);

        // Generate pre-signed URL valid for 1 hour for immediate client access
        String presignedUrl = generatePresignedUrl(bucket, objectKey);
        saved.setStorageUrl(presignedUrl);

        return DocumentResponse.fromEntity(saved);
    }

    // ── Fetch by claim ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByClaim(UUID claimId) {
        return documentRepository.findByClaimIdOrderByCreatedAtDesc(claimId)
                .stream()
                .map(doc -> {
                    // Refresh pre-signed URL on every fetch — they expire after 1 hour
                    String url = generatePresignedUrl(doc.getStorageBucket(), doc.getStorageKey());
                    doc.setStorageUrl(url);
                    return DocumentResponse.fromEntity(doc);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getByClaimAndType(UUID claimId, String documentType) {
        return documentRepository.findByClaimIdAndDocumentType(claimId, documentType)
                .stream()
                .map(doc -> {
                    doc.setStorageUrl(generatePresignedUrl(doc.getStorageBucket(), doc.getStorageKey()));
                    return DocumentResponse.fromEntity(doc);
                })
                .collect(Collectors.toList());
    }

    // ── Download stream ───────────────────────────────────────────────────────

    /**
     * Returns an InputStream for the document — used for direct streaming download.
     * Caller is responsible for closing the stream.
     */
    public InputStream downloadStream(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(doc.getStorageBucket())
                    .object(doc.getStorageKey())
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Download failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Map document type to the appropriate MinIO bucket */
    private String resolveBucket(String documentType) {
        return switch (documentType) {
            case "ACCIDENT_PHOTO", "VEHICLE_DAMAGE_PHOTO" -> bucketPhotos;
            case "ASSESSMENT_REPORT", "REPAIR_INVOICE"    -> bucketReports;
            default                                        -> bucketDocuments;
        };
    }

    /** Generates a 1-hour pre-signed URL for direct browser access to the file */
    private String generatePresignedUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for {}/{}: {}", bucket, objectKey, e.getMessage());
            return null;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
