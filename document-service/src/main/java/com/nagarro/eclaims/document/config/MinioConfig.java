package com.nagarro.eclaims.document.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client configuration.
 *
 * <p>Creates a singleton {@link MinioClient} bean connected to the local MinIO instance.
 * In production, these values come from Kubernetes secrets or Cloud Secret Manager.</p>
 */
@Configuration
public class MinioConfig {

    @Value("${eclaims.minio.endpoint}")
    private String endpoint;

    @Value("${eclaims.minio.access-key}")
    private String accessKey;

    @Value("${eclaims.minio.secret-key}")
    private String secretKey;

    /**
     * MinioClient — thread-safe, can be shared across all service calls.
     * Equivalent to the S3 client in AWS SDK; compatible with GCS/Azure via endpoint swap.
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
