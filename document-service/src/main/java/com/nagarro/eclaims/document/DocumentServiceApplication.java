package com.nagarro.eclaims.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * eClaims — Document Service (Port 8083)
 *
 * Responsibilities:
 *  - Accept file uploads from Customer Portal, Surveyor App, Adjustor Portal
 *  - Store binary files in MinIO (S3-compatible object storage)
 *  - Persist document metadata in document_db via Spring Data JPA
 *  - Serve pre-signed download URLs (1-hour expiry)
 *  - Enforce 7-year document retention policy
 */
@SpringBootApplication
@EnableJpaAuditing
public class DocumentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
