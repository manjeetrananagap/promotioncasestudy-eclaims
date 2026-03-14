package com.nagarro.eclaims.claims.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nagarro.eclaims.claims.entity.Claim;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.entity.ClaimStatusHistory;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Full claim detail including status audit history. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimResponse {

    private UUID        id;
    private String      claimNumber;
    private String      policyId;
    private String      policyHolderName;
    private String      vehicleReg;
    private String      vehicleMake;
    private String      vehicleModel;
    private ClaimStatus status;
    private String      statusLabel;

    private BigDecimal  accidentLat;
    private BigDecimal  accidentLng;
    private String      accidentAddress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate   incidentDate;
    private String      incidentDescription;

    private BigDecimal  estimatedAmount;
    private BigDecimal  approvedAmount;
    private BigDecimal  deductibleAmount;
    private BigDecimal  insurerContribution;
    private BigDecimal  customerContribution;

    private UUID        assignedSurveyorId;
    private UUID        assignedWorkshopId;
    private String      workflowInstanceId;
    private String      submittedByUserId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private List<StatusHistoryDto> statusHistory;

    public static ClaimResponse fromEntity(Claim c) {
        return ClaimResponse.builder()
                .id(c.getId()).claimNumber(c.getClaimNumber())
                .policyId(c.getPolicyId()).policyHolderName(c.getPolicyHolderName())
                .vehicleReg(c.getVehicleReg()).vehicleMake(c.getVehicleMake())
                .vehicleModel(c.getVehicleModel())
                .status(c.getStatus()).statusLabel(label(c.getStatus()))
                .accidentLat(c.getAccidentLat()).accidentLng(c.getAccidentLng())
                .accidentAddress(c.getAccidentAddress())
                .incidentDate(c.getIncidentDate()).incidentDescription(c.getIncidentDescription())
                .estimatedAmount(c.getEstimatedAmount()).approvedAmount(c.getApprovedAmount())
                .deductibleAmount(c.getDeductibleAmount())
                .insurerContribution(c.getInsurerContribution())
                .customerContribution(c.getCustomerContribution())
                .assignedSurveyorId(c.getAssignedSurveyorId())
                .assignedWorkshopId(c.getAssignedWorkshopId())
                .workflowInstanceId(c.getWorkflowInstanceId())
                .submittedByUserId(c.getSubmittedByUserId())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .statusHistory(c.getStatusHistory() == null ? null :
                    c.getStatusHistory().stream().map(StatusHistoryDto::from).collect(Collectors.toList()))
                .build();
    }

    private static String label(ClaimStatus s) {
        return switch (s) {
            case SUBMITTED          -> "Submitted";
            case VALIDATED          -> "Validated";
            case SURVEYOR_ASSIGNED  -> "Surveyor Assigned";
            case ASSESSED           -> "Assessment Complete";
            case APPROVED           -> "Approved";
            case REJECTED           -> "Rejected";
            case WORKSHOP_ASSIGNED  -> "Workshop Assigned";
            case REPAIR_IN_PROGRESS -> "Repair In Progress";
            case REPAIR_COMPLETED   -> "Repair Completed";
            case PAYMENT_PENDING    -> "Payment Pending";
            case CLOSED             -> "Closed";
            case CANCELLED          -> "Cancelled";
        };
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusHistoryDto {
        private ClaimStatus oldStatus;
        private ClaimStatus newStatus;
        private String changedBy;
        private String note;
        private String eventSource;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime changedAt;

        public static StatusHistoryDto from(ClaimStatusHistory h) {
            return StatusHistoryDto.builder()
                    .oldStatus(h.getOldStatus()).newStatus(h.getNewStatus())
                    .changedBy(h.getChangedBy()).note(h.getNote())
                    .eventSource(h.getEventSource()).changedAt(h.getChangedAt())
                    .build();
        }
    }
}
