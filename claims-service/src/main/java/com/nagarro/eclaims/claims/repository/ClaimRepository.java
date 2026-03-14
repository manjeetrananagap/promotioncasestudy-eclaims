package com.nagarro.eclaims.claims.repository;

import com.nagarro.eclaims.claims.entity.Claim;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Claim entities.
 * JpaRepository provides: save, findById, findAll, delete, count, pagination.
 * Custom queries use JPQL (database-agnostic).
 */
@Repository
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByClaimNumberAndDeletedFalse(String claimNumber);

    Page<Claim> findBySubmittedByUserIdAndDeletedFalseOrderByCreatedAtDesc(
            String userId, Pageable pageable);

    Page<Claim> findByStatusAndDeletedFalse(ClaimStatus status, Pageable pageable);

    long countByStatusAndDeletedFalse(ClaimStatus status);

    /** Eager-fetch history to avoid N+1 on detail view */
    @Query("""
        SELECT c FROM Claim c
        LEFT JOIN FETCH c.statusHistory
        WHERE c.id = :id AND c.deleted = false
        """)
    Optional<Claim> findByIdWithHistory(@Param("id") UUID id);

    @Query("""
        SELECT c FROM Claim c
        LEFT JOIN FETCH c.statusHistory
        WHERE c.claimNumber = :claimNumber AND c.deleted = false
        """)
    Optional<Claim> findByClaimNumberWithHistory(@Param("claimNumber") String claimNumber);

    /** Returns [ClaimStatus, count] pairs for dashboard summary */
    @Query("SELECT c.status, COUNT(c) FROM Claim c WHERE c.deleted = false GROUP BY c.status")
    List<Object[]> countGroupedByStatus();

    @Modifying
    @Query("UPDATE Claim c SET c.deleted = true WHERE c.id = :id")
    void softDelete(@Param("id") UUID id);
}
