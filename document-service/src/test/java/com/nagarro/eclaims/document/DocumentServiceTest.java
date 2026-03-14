package com.nagarro.eclaims.document;

import com.nagarro.eclaims.document.dto.DocumentResponse;
import com.nagarro.eclaims.document.entity.Document;
import com.nagarro.eclaims.document.repository.DocumentRepository;
import com.nagarro.eclaims.document.service.DocumentService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link DocumentService}.
 * Uses Mockito to mock MinioClient and DocumentRepository.
 * No real MinIO or PostgreSQL needed.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private MinioClient minioClient;

    @InjectMocks private DocumentService documentService;

    private UUID claimId;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID();
        // Inject @Value fields via reflection for unit test
        org.springframework.test.util.ReflectionTestUtils.setField(
                documentService, "bucketDocuments", "eclaims-documents");
        org.springframework.test.util.ReflectionTestUtils.setField(
                documentService, "bucketPhotos", "eclaims-photos");
        org.springframework.test.util.ReflectionTestUtils.setField(
                documentService, "bucketReports", "eclaims-reports");
    }

    @Test
    @DisplayName("upload: saves metadata to repository after successful MinIO put")
    void upload_savesMetadataAfterMinioUpload() throws Exception {

        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "accident.jpg", "image/jpeg",
                "fake-image-bytes".getBytes());

        Document savedDoc = Document.builder()
                .id(UUID.randomUUID())
                .claimId(claimId)
                .documentType("ACCIDENT_PHOTO")
                .originalName("accident.jpg")
                .storageBucket("eclaims-photos")
                .storageKey(claimId + "/ACCIDENT_PHOTO/test.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(16L)
                .uploadSource("CUSTOMER_PORTAL")
                .build();

        // MinioClient.putObject returns ObjectWriteResponse — mock it
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);
        // presignedUrl will throw (mock not set), handled gracefully
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/presigned/url");

        // Act
        DocumentResponse response = documentService.upload(
                claimId, "ACCIDENT_PHOTO", file, "user-001", "CUSTOMER_PORTAL");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getClaimId()).isEqualTo(claimId);
        assertThat(response.getDocumentType()).isEqualTo("ACCIDENT_PHOTO");
        assertThat(response.getOriginalName()).isEqualTo("accident.jpg");

        // Verify MinIO was called once
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        // Verify JPA save was called
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("getDocumentsByClaim: returns empty list when no documents exist")
    void getDocumentsByClaim_returnsEmptyList() {
        when(documentRepository.findByClaimIdOrderByCreatedAtDesc(claimId))
                .thenReturn(List.of());

        List<DocumentResponse> result = documentService.getDocumentsByClaim(claimId);
        assertThat(result).isEmpty();
    }
}
