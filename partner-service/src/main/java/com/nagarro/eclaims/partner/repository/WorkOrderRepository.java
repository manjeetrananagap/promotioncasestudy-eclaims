package com.nagarro.eclaims.partner.repository;

import com.nagarro.eclaims.partner.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    Optional<WorkOrder> findByClaimId(UUID claimId);
    Optional<WorkOrder> findByClaimIdAndStatusNot(UUID claimId, String status);
}
