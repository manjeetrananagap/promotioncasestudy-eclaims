package com.nagarro.eclaims.document.repository;

import com.nagarro.eclaims.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Document} entities.
 *
 * <p>Provides standard CRUD + pagination via {@link JpaRepository}.
 * Custom finders follow Spring Data's method naming convention —
 * no JPQL or SQL needed for these queries.</p>
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Get all documents for a specific claim — used in claim detail view */
    List<Document> findByClaimIdOrderByCreatedAtDesc(UUID claimId);

    /** Get documents of a specific type for a claim (e.g. all ACCIDENT_PHOTO) */
    List<Document> findByClaimIdAndDocumentType(UUID claimId, String documentType);

    /** Count documents per claim — used for completeness checks */
    long countByClaimId(UUID claimId);

    /** Check if a specific document type exists for a claim */
    boolean existsByClaimIdAndDocumentType(UUID claimId, String documentType);
}
